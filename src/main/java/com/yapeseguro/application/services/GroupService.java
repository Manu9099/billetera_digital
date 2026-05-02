package com.yapeseguro.application.services;

import com.yapeseguro.api.dto.request.AddGroupMemberRequest;
import com.yapeseguro.api.dto.request.CreateGroupRequest;
import com.yapeseguro.api.dto.request.PayGroupContributionRequest;
import com.yapeseguro.api.dto.response.GroupContributionResponse;
import com.yapeseguro.api.dto.response.GroupMemberResponse;
import com.yapeseguro.api.dto.response.GroupResponse;
import com.yapeseguro.infrastructure.persistence.entities.GroupEntity;
import com.yapeseguro.infrastructure.persistence.entities.GroupMemberEntity;
import com.yapeseguro.infrastructure.persistence.entities.TransactionEntity;
import com.yapeseguro.infrastructure.persistence.entities.UserEntity;
import com.yapeseguro.infrastructure.persistence.entities.WalletEntity;
import com.yapeseguro.infrastructure.persistence.repositories.GroupMemberRepository;
import com.yapeseguro.infrastructure.persistence.repositories.GroupRepository;
import com.yapeseguro.infrastructure.persistence.repositories.TransactionRepository;
import com.yapeseguro.infrastructure.persistence.repositories.UserRepository;
import com.yapeseguro.infrastructure.persistence.repositories.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final TransactionRepository transactionRepository;
    private final ReceiptService receiptService;

    @Transactional
    public GroupResponse createGroup(
            CreateGroupRequest request,
            String username
    ) {
        UserEntity creator = getUserByUsername(username);

        BigDecimal totalAmount = request.getTotalAmount();

        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto total debe ser mayor a cero");
        }

        GroupEntity.GroupType groupType = parseGroupType(request.getGroupType());

        GroupEntity group = GroupEntity.builder()
                .creatorUser(creator)
                .groupName(normalizeRequired(request.getGroupName(), "El nombre del grupo es obligatorio"))
                .description(normalize(request.getDescription()))
                .groupType(groupType)
                .totalAmount(totalAmount)
                .currentAmount(BigDecimal.ZERO)
                .currency(getPersonalWallet(creator).getCurrency())
                .memberCount(0)
                .status(GroupEntity.GroupStatus.ACTIVE)
                .targetDate(request.getTargetDate())
                .build();

        GroupEntity savedGroup = groupRepository.save(group);

        Set<UUID> participantIds = new LinkedHashSet<>();
        participantIds.add(creator.getId());

        if (request.getMemberUserIds() != null) {
            participantIds.addAll(request.getMemberUserIds());
        }

        BigDecimal perPersonAmount = calculatePerPersonAmount(
                savedGroup.getTotalAmount(),
                participantIds.size()
        );

        savedGroup.setPerPersonAmount(perPersonAmount);

        List<GroupMemberEntity> members = new ArrayList<>();

        for (UUID participantId : participantIds) {
            UserEntity participant = userRepository.findById(participantId)
                    .orElseThrow(() -> new IllegalArgumentException("Usuario miembro no encontrado"));

            members.add(
                    GroupMemberEntity.builder()
                            .group(savedGroup)
                            .user(participant)
                            .userName(fullName(participant))
                            .amountToPay(perPersonAmount)
                            .amountPaid(BigDecimal.ZERO)
                            .status(GroupMemberEntity.MemberStatus.PENDING)
                            .build()
            );
        }

        groupMemberRepository.saveAll(members);

        savedGroup.setMemberCount(members.size());

        GroupEntity finalGroup = groupRepository.save(savedGroup);

        return toResponse(finalGroup, groupMemberRepository.findByGroupOrderByAddedAtAsc(finalGroup));
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> getMyGroups(String username) {
        UserEntity user = getUserByUsername(username);

        return groupRepository.findVisibleToUser(user)
                .stream()
                .map(group -> toResponse(group, groupMemberRepository.findByGroupOrderByAddedAtAsc(group)))
                .toList();
    }

    @Transactional(readOnly = true)
    public GroupResponse getGroupById(
            UUID groupId,
            String username
    ) {
        UserEntity user = getUserByUsername(username);

        GroupEntity group = groupRepository.findVisibleToUserDetailed(groupId, user)
                .orElseThrow(() -> new IllegalArgumentException("Grupo no encontrado"));

        return toResponse(group, groupMemberRepository.findByGroupOrderByAddedAtAsc(group));
    }

    @Transactional
    public GroupResponse addMember(
            UUID groupId,
            AddGroupMemberRequest request,
            String username
    ) {
        UserEntity creator = getUserByUsername(username);

        GroupEntity group = groupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Grupo no encontrado"));

        validateCreator(group, creator);

        if (group.getStatus() != GroupEntity.GroupStatus.ACTIVE) {
            throw new IllegalArgumentException("Solo puedes agregar miembros a grupos activos");
        }

        UserEntity newMember = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Usuario miembro no encontrado"));

        if (groupMemberRepository.existsByGroupAndUser(group, newMember)) {
            throw new IllegalArgumentException("El usuario ya pertenece a este grupo");
        }

        BigDecimal amountToPay = request.getAmountToPay() != null
                ? request.getAmountToPay()
                : resolveDefaultAmountToPay(group);

        GroupMemberEntity member = GroupMemberEntity.builder()
                .group(group)
                .user(newMember)
                .userName(resolveMemberName(request, newMember))
                .amountToPay(amountToPay)
                .amountPaid(BigDecimal.ZERO)
                .status(GroupMemberEntity.MemberStatus.PENDING)
                .build();

        groupMemberRepository.save(member);

        group.setMemberCount((int) groupMemberRepository.countByGroup(group));

        GroupEntity savedGroup = groupRepository.save(group);

        return toResponse(savedGroup, groupMemberRepository.findByGroupOrderByAddedAtAsc(savedGroup));
    }

    @Transactional
    public GroupContributionResponse payContribution(
            UUID groupId,
            UUID memberId,
            PayGroupContributionRequest request,
            String username
    ) {
        UserEntity payer = getUserByUsername(username);

        GroupEntity group = groupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Grupo no encontrado"));

        if (group.getStatus() != GroupEntity.GroupStatus.ACTIVE) {
            throw new IllegalArgumentException("El grupo no está activo");
        }

        GroupMemberEntity member = groupMemberRepository.findByIdAndGroup(memberId, group)
                .orElseThrow(() -> new IllegalArgumentException("Miembro no encontrado"));

        if (!member.getUser().getId().equals(payer.getId())) {
            throw new IllegalArgumentException("Solo el miembro puede pagar su propia cuota");
        }

        BigDecimal amount = resolveContributionAmount(group, member, request);

        OffsetDateTime now = OffsetDateTime.now();

        TransactionEntity transaction = null;

        boolean payerIsCreator = group.getCreatorUser().getId().equals(payer.getId());

        if (!payerIsCreator) {
            transaction = transferToCreatorWallet(
                    group,
                    member,
                    amount,
                    normalize(request.getNotes()),
                    now
            );
        }

        BigDecimal newMemberPaid = member.getAmountPaid().add(amount);
        BigDecimal newGroupCurrent = group.getCurrentAmount().add(amount);

        if (newGroupCurrent.compareTo(group.getTotalAmount()) > 0) {
            newGroupCurrent = group.getTotalAmount();
        }

        member.setAmountPaid(newMemberPaid);
        member.setStatus(resolveMemberStatus(member));
        member.setPaidAt(member.getStatus() == GroupMemberEntity.MemberStatus.PAID ? now : null);

        group.setCurrentAmount(newGroupCurrent);

        if (group.getCurrentAmount().compareTo(group.getTotalAmount()) >= 0) {
            group.setStatus(GroupEntity.GroupStatus.COMPLETED);
            group.setCompletedAt(now);
        }

        groupMemberRepository.save(member);
        GroupEntity savedGroup = groupRepository.save(group);

        return GroupContributionResponse.builder()
                .groupId(savedGroup.getId())
                .memberId(member.getId())
                .transactionId(transaction != null ? transaction.getId() : null)
                .transactionReference(transaction != null ? transaction.getReference() : null)
                .amountPaid(amount)
                .memberTotalPaid(member.getAmountPaid())
                .groupCurrentAmount(savedGroup.getCurrentAmount())
                .groupRemainingAmount(remainingGroupAmount(savedGroup))
                .memberStatus(member.getStatus().name())
                .groupStatus(savedGroup.getStatus().name())
                .build();
    }

    @Transactional
    public void cancelGroup(
            UUID groupId,
            String username
    ) {
        UserEntity creator = getUserByUsername(username);

        GroupEntity group = groupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Grupo no encontrado"));

        validateCreator(group, creator);

        if (group.getStatus() == GroupEntity.GroupStatus.CANCELLED) {
            return;
        }

        if (group.getStatus() == GroupEntity.GroupStatus.COMPLETED) {
            throw new IllegalArgumentException("No puedes cancelar un grupo completado");
        }

        group.setStatus(GroupEntity.GroupStatus.CANCELLED);

        groupRepository.save(group);
    }

    private TransactionEntity transferToCreatorWallet(
            GroupEntity group,
            GroupMemberEntity member,
            BigDecimal amount,
            String notes,
            OffsetDateTime now
    ) {
        WalletEntity sourceWalletRef = getPersonalWallet(member.getUser());
        WalletEntity targetWalletRef = getPersonalWallet(group.getCreatorUser());

        WalletPair lockedWallets = lockWalletsInStableOrder(
                sourceWalletRef.getId(),
                targetWalletRef.getId()
        );

        WalletEntity sourceWallet = lockedWallets.sourceWallet();
        WalletEntity targetWallet = lockedWallets.targetWallet();

        validateWalletIsActive(sourceWallet, "La billetera del miembro no está activa");
        validateWalletIsActive(targetWallet, "La billetera del creador no está activa");

        if (!sourceWallet.getCurrency().equals(targetWallet.getCurrency())) {
            throw new IllegalArgumentException("Las billeteras no usan la misma moneda");
        }

        if (safe(sourceWallet.getAvailableBalance()).compareTo(amount) < 0) {
            throw new IllegalArgumentException("Saldo insuficiente");
        }

        applyDebit(sourceWallet, amount);
        applyCredit(targetWallet, amount);

        sourceWallet.setLastTransactionAt(now);
        targetWallet.setLastTransactionAt(now);

        walletRepository.saveAll(List.of(sourceWallet, targetWallet));

        TransactionEntity transaction = TransactionEntity.builder()
                .walletFrom(sourceWallet)
                .walletTo(targetWallet)
                .amount(amount)
                .currency(sourceWallet.getCurrency())
                .type(TransactionEntity.TxType.P2P)
                .status(TransactionEntity.TxStatus.COMPLETED)
                .marketplaceStatus(TransactionEntity.MpStatus.NORMAL)
                .description("Aporte a cuenta grupal: " + group.getGroupName())
                .concept("Cuenta grupal")
                .reference(generateUniqueReference("GRP"))
                .notes(buildGroupContributionNote(group, member, notes))
                .completedAt(now)
                .build();

        TransactionEntity savedTransaction = transactionRepository.save(transaction);

        receiptService.generateReceiptForTransaction(savedTransaction.getId());

        return savedTransaction;
    }

    private BigDecimal resolveContributionAmount(
            GroupEntity group,
            GroupMemberEntity member,
            PayGroupContributionRequest request
    ) {
        BigDecimal memberRemaining = member.getAmountToPay().subtract(member.getAmountPaid());
        BigDecimal groupRemaining = remainingGroupAmount(group);

        if (memberRemaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Este miembro ya completó su pago");
        }

        if (groupRemaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El grupo ya alcanzó el monto objetivo");
        }

        BigDecimal amount = request.getAmount() != null
                ? request.getAmount()
                : memberRemaining.min(groupRemaining);

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a cero");
        }

        if (amount.compareTo(memberRemaining) > 0) {
            throw new IllegalArgumentException("El monto excede el pendiente del miembro");
        }

        if (amount.compareTo(groupRemaining) > 0) {
            throw new IllegalArgumentException("El monto excede el pendiente del grupo");
        }

        return amount;
    }

    private GroupMemberEntity.MemberStatus resolveMemberStatus(GroupMemberEntity member) {
        if (member.getAmountPaid().compareTo(member.getAmountToPay()) >= 0) {
            return GroupMemberEntity.MemberStatus.PAID;
        }

        if (member.getAmountPaid().compareTo(BigDecimal.ZERO) > 0) {
            return GroupMemberEntity.MemberStatus.CONFIRMED;
        }

        return GroupMemberEntity.MemberStatus.PENDING;
    }

    private BigDecimal calculatePerPersonAmount(
            BigDecimal totalAmount,
            int memberCount
    ) {
        if (memberCount <= 0) {
            return totalAmount;
        }

        return totalAmount.divide(BigDecimal.valueOf(memberCount), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveDefaultAmountToPay(GroupEntity group) {
        if (group.getPerPersonAmount() != null && group.getPerPersonAmount().compareTo(BigDecimal.ZERO) > 0) {
            return group.getPerPersonAmount();
        }

        return remainingGroupAmount(group);
    }

    private GroupResponse toResponse(
            GroupEntity group,
            List<GroupMemberEntity> members
    ) {
        int paidMemberCount = (int) members.stream()
                .filter(member -> member.getStatus() == GroupMemberEntity.MemberStatus.PAID)
                .count();

        int pendingMemberCount = members.size() - paidMemberCount;

        return GroupResponse.builder()
                .id(group.getId())
                .creatorUserId(group.getCreatorUser().getId())
                .creatorName(fullName(group.getCreatorUser()))
                .groupName(group.getGroupName())
                .description(group.getDescription())
                .groupType(group.getGroupType().name())
                .totalAmount(group.getTotalAmount())
                .currentAmount(group.getCurrentAmount())
                .remainingAmount(remainingGroupAmount(group))
                .perPersonAmount(group.getPerPersonAmount())
                .progressPercentage(calculateProgressPercentage(group))
                .currency(group.getCurrency())
                .memberCount(group.getMemberCount())
                .paidMemberCount(paidMemberCount)
                .pendingMemberCount(pendingMemberCount)
                .status(group.getStatus().name())
                .targetDate(group.getTargetDate())
                .completedAt(group.getCompletedAt())
                .createdAt(group.getCreatedAt())
                .updatedAt(group.getUpdatedAt())
                .members(members.stream().map(this::toMemberResponse).toList())
                .build();
    }

    private GroupMemberResponse toMemberResponse(GroupMemberEntity member) {
        return GroupMemberResponse.builder()
                .id(member.getId())
                .userId(member.getUser().getId())
                .userName(resolveStoredMemberName(member))
                .amountToPay(member.getAmountToPay())
                .amountPaid(member.getAmountPaid())
                .remainingAmount(member.getAmountToPay().subtract(member.getAmountPaid()))
                .status(member.getStatus().name())
                .paidAt(member.getPaidAt())
                .addedAt(member.getAddedAt())
                .updatedAt(member.getUpdatedAt())
                .build();
    }

    private BigDecimal remainingGroupAmount(GroupEntity group) {
        return group.getTotalAmount().subtract(group.getCurrentAmount()).max(BigDecimal.ZERO);
    }

    private BigDecimal calculateProgressPercentage(GroupEntity group) {
        if (group.getTotalAmount() == null || group.getTotalAmount().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return group.getCurrentAmount()
                .multiply(BigDecimal.valueOf(100))
                .divide(group.getTotalAmount(), 2, RoundingMode.HALF_UP);
    }

    private WalletPair lockWalletsInStableOrder(UUID sourceWalletId, UUID targetWalletId) {
        WalletEntity firstLocked;
        WalletEntity secondLocked;

        if (sourceWalletId.compareTo(targetWalletId) <= 0) {
            firstLocked = lockWallet(sourceWalletId);
            secondLocked = lockWallet(targetWalletId);
        } else {
            firstLocked = lockWallet(targetWalletId);
            secondLocked = lockWallet(sourceWalletId);
        }

        WalletEntity sourceWallet = firstLocked.getId().equals(sourceWalletId)
                ? firstLocked
                : secondLocked;

        WalletEntity targetWallet = firstLocked.getId().equals(targetWalletId)
                ? firstLocked
                : secondLocked;

        return new WalletPair(sourceWallet, targetWallet);
    }

    private WalletEntity lockWallet(UUID walletId) {
        return walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Billetera no encontrada"));
    }

    private WalletEntity getPersonalWallet(UserEntity user) {
        return walletRepository
                .findByUserAndWalletType(user, WalletEntity.WalletType.PERSONAL)
                .orElseThrow(() -> new IllegalArgumentException("El usuario no tiene billetera personal"));
    }

    private void validateWalletIsActive(WalletEntity wallet, String message) {
        if (!wallet.isActive()) {
            throw new IllegalArgumentException(message);
        }
    }

    private void validateCreator(
            GroupEntity group,
            UserEntity user
    ) {
        if (!group.getCreatorUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Solo el creador puede modificar este grupo");
        }
    }

    private void applyDebit(WalletEntity wallet, BigDecimal amount) {
        wallet.setBalance(safe(wallet.getBalance()).subtract(amount));
        wallet.setAvailableBalance(safe(wallet.getAvailableBalance()).subtract(amount));
        wallet.setMonthlyExpenses(safe(wallet.getMonthlyExpenses()).add(amount));
        wallet.setDailyTxCount(wallet.getDailyTxCount() + 1);
    }

    private void applyCredit(WalletEntity wallet, BigDecimal amount) {
        wallet.setBalance(safe(wallet.getBalance()).add(amount));
        wallet.setAvailableBalance(safe(wallet.getAvailableBalance()).add(amount));
        wallet.setMonthlyRevenue(safe(wallet.getMonthlyRevenue()).add(amount));
        wallet.setDailyTxCount(wallet.getDailyTxCount() + 1);
    }

    private String generateUniqueReference(String prefix) {
        String reference;

        do {
            reference = prefix
                    + "-"
                    + UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, 16)
                    .toUpperCase();
        } while (transactionRepository.existsByReference(reference));

        return reference;
    }

    private GroupEntity.GroupType parseGroupType(String groupType) {
        try {
            return GroupEntity.GroupType.valueOf(groupType.trim().toUpperCase());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Tipo de grupo inválido");
        }
    }

    private UserEntity getUserByUsername(String username) {
        return userRepository.findByEmail(username)
                .or(() -> userRepository.findByPhoneNumber(username))
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
    }

    private String resolveMemberName(
            AddGroupMemberRequest request,
            UserEntity user
    ) {
        String normalized = normalize(request.getUserName());

        return normalized != null ? normalized : fullName(user);
    }

    private String resolveStoredMemberName(GroupMemberEntity member) {
        String normalized = normalize(member.getUserName());

        return normalized != null ? normalized : fullName(member.getUser());
    }

    private String buildGroupContributionNote(
            GroupEntity group,
            GroupMemberEntity member,
            String notes
    ) {
        String base = "GROUP_ID=" + group.getId() + "; MEMBER_ID=" + member.getId();

        if (notes == null) {
            return base;
        }

        return base + "; " + notes;
    }

    private String fullName(UserEntity user) {
        return (user.getFirstName() + " " + user.getLastName()).trim();
    }

    private BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalize(value);

        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }

        return normalized;
    }

    private record WalletPair(
            WalletEntity sourceWallet,
            WalletEntity targetWallet
    ) {
    }
}