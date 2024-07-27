package guru.qa.niffler.service;

import guru.qa.niffler.data.CurrencyValues;
import guru.qa.niffler.data.FriendshipEntity;
import guru.qa.niffler.data.FriendshipStatus;
import guru.qa.niffler.data.UserEntity;
import guru.qa.niffler.data.repository.UserRepository;
import guru.qa.niffler.ex.NotFoundException;
import guru.qa.niffler.ex.SameUsernameException;
import guru.qa.niffler.model.UserJson;
import guru.qa.niffler.model.UserJsonBulk;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static guru.qa.niffler.model.FriendState.FRIEND;
import static guru.qa.niffler.model.FriendState.INVITE_RECEIVED;
import static guru.qa.niffler.model.FriendState.INVITE_SENT;
import static java.util.Comparator.comparing;

@Component
public class UserDataService {

    private static final Logger LOG = LoggerFactory.getLogger(UserDataService.class);

    private static final CurrencyValues DEFAULT_USER_CURRENCY = CurrencyValues.RUB;
    private final UserRepository userRepository;

    @Autowired
    public UserDataService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    @KafkaListener(topics = "users", groupId = "userdata")
    public void listener(@Payload UserJson user, ConsumerRecord<String, UserJson> cr) {
        userRepository.findByUsername(user.username())
                .ifPresentOrElse(
                        u -> LOG.info("### User already exist in DB, kafka event will be skipped: {}", cr.toString()),
                        () -> {
                            LOG.info("### Kafka consumer record: {}", cr.toString());

                            UserEntity userDataEntity = new UserEntity();
                            userDataEntity.setUsername(user.username());
                            userDataEntity.setCurrency(DEFAULT_USER_CURRENCY);
                            UserEntity userEntity = userRepository.save(userDataEntity);

                            LOG.info(
                                    "### User '{}' successfully saved to database with id: {}",
                                    user.username(),
                                    userEntity.getId()
                            );
                        }
                );
    }

    @Transactional
    public @Nonnull
    UserJson update(@Nonnull UserJson user) {
        UserEntity userEntity = userRepository.findByUsername(user.username())
                .orElseGet(() -> {
                    UserEntity emptyUser = new UserEntity();
                    emptyUser.setUsername(user.username());
                    emptyUser.setCurrency(user.currency() == null ? DEFAULT_USER_CURRENCY : user.currency());
                    return emptyUser;
                });

        userEntity.setFullname(user.fullname());
        userEntity.setCurrency(user.currency());
        userEntity.setPhoto(user.photo() != null ? user.photo().getBytes(StandardCharsets.UTF_8) : null);
        userEntity.setPhotoSmall(new SmallPhoto(100, 100, user.photo()).bytes());
        UserEntity saved = userRepository.save(userEntity);
        return UserJson.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public @Nonnull
    UserJson getCurrentUser(@Nonnull String username) {
        return UserJson.fromEntity(
                userRepository.findByUsername(username)
                        .orElse(new UserEntity())
        );
    }

    @Transactional(readOnly = true)
    public @Nonnull
    List<UserJsonBulk> allUsers(@Nonnull String username,
                                @Nullable String searchQuery) {
        List<UserEntity> usersFromDb = searchQuery == null
                ? userRepository.findByUsernameNot(username)
                : userRepository.findByUsernameNot(username, searchQuery);

        return usersFromDb.stream()
                .map(ue -> mapToUserJsonWithFriendshipState(username, ue))
                .sorted(
                        Comparator.comparing(UserJsonBulk::friendState,
                                Comparator.nullsLast(Comparator.reverseOrder())
                        )
                )
                .toList();
    }

    @Transactional(readOnly = true)
    public @Nonnull
    Page<UserJsonBulk> allUsers(@Nonnull String username,
                                @Nonnull Pageable pageable,
                                @Nullable String searchQuery) {
        Page<UserEntity> usersFromDb = searchQuery == null
                ? userRepository.findByUsernameNot(username, pageable)
                : userRepository.findByUsernameNot(username, searchQuery, pageable);

        return usersFromDb.map(ue -> mapToUserJsonWithFriendshipState(username, ue));
    }

    @Transactional(readOnly = true)
    public @Nonnull
    List<UserJsonBulk> friends(@Nonnull String username,
                               @Nullable String searchQuery) {
        List<UserEntity> usersFromDb = searchQuery == null
                ? userRepository.findFriends(getRequiredUser(username))
                : userRepository.findFriends(getRequiredUser(username), searchQuery);

        return usersFromDb.stream()
                .map(f -> mapToUserJsonWithFriendshipState(username, f))
                .sorted(comparing(UserJsonBulk::friendState))
                .toList();
    }

    @Transactional(readOnly = true)
    public @Nonnull
    Page<UserJsonBulk> friends(@Nonnull String username,
                               @Nonnull Pageable pageable,
                               @Nullable String searchQuery) {
        Page<UserEntity> usersFromDb = searchQuery == null
                ? userRepository.findFriends(getRequiredUser(username), pageable)
                : userRepository.findFriends(getRequiredUser(username), searchQuery, pageable);

        return new PageImpl<>(
                usersFromDb.getContent().stream()
                        .map(f -> mapToUserJsonWithFriendshipState(username, f))
                        .sorted(comparing(UserJsonBulk::friendState))
                        .toList(),
                pageable,
                usersFromDb.getTotalElements()
        );
    }

    @Transactional
    public UserJson createFriendshipRequest(@Nonnull String username, @Nonnull String targetUsername) {
        if (Objects.equals(username, targetUsername)) {
            throw new SameUsernameException("Can`t create friendship request for self user");
        }
        UserEntity currentUser = getRequiredUser(username);
        UserEntity targetUser = getRequiredUser(targetUsername);
        currentUser.addFriends(FriendshipStatus.PENDING, targetUser);
        userRepository.save(currentUser);
        return UserJson.fromEntity(targetUser, INVITE_SENT);
    }

    @Transactional
    public @Nonnull
    UserJson acceptFriendshipRequest(@Nonnull String username, @Nonnull String targetUsername) {
        if (Objects.equals(username, targetUsername)) {
            throw new SameUsernameException("Can`t accept friendship request for self user");
        }
        UserEntity currentUser = getRequiredUser(username);
        UserEntity targetUser = getRequiredUser(targetUsername);

        FriendshipEntity invite = currentUser.getFriendshipAddressees()
                .stream()
                .filter(fe -> fe.getRequester().getUsername().equals(targetUser.getUsername()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Can`t find invitation from username: '" + targetUsername + "'"));

        invite.setStatus(FriendshipStatus.ACCEPTED);
        currentUser.addFriends(FriendshipStatus.ACCEPTED, targetUser);
        userRepository.save(currentUser);
        return UserJson.fromEntity(targetUser, FRIEND);
    }

    @Transactional
    public @Nonnull
    UserJson declineFriendshipRequest(@Nonnull String username, @Nonnull String targetUsername) {
        if (Objects.equals(username, targetUsername)) {
            throw new SameUsernameException("Can`t decline friendship request for self user");
        }
        UserEntity currentUser = getRequiredUser(username);
        UserEntity targetUser = getRequiredUser(targetUsername);

        currentUser.removeInvites(targetUser);
        targetUser.removeFriends(currentUser);

        userRepository.save(currentUser);
        userRepository.save(targetUser);
        return UserJson.fromEntity(targetUser);
    }

    @Transactional
    public void removeFriend(@Nonnull String username, @Nonnull String targetUsername) {
        if (Objects.equals(username, targetUsername)) {
            throw new SameUsernameException("Can`t remove friendship relation for self user");
        }
        UserEntity currentUser = getRequiredUser(username);
        UserEntity targetUser = getRequiredUser(targetUsername);

        currentUser.removeFriends(targetUser);
        currentUser.removeInvites(targetUser);
        targetUser.removeFriends(currentUser);
        targetUser.removeInvites(currentUser);

        userRepository.save(currentUser);
        userRepository.save(targetUser);
    }

    @Nonnull
    UserEntity getRequiredUser(@Nonnull String username) {
        return userRepository.findByUsername(username).orElseThrow(
                () -> new NotFoundException("Can`t find user by username: '" + username + "'")
        );
    }

    @Nonnull
    UserJsonBulk mapToUserJsonWithFriendshipState(@Nonnull String username,
                                                  @Nonnull UserEntity userEntity) {
        List<FriendshipEntity> requests = userEntity.getFriendshipRequests();
        List<FriendshipEntity> addresses = userEntity.getFriendshipAddressees();

        if (!requests.isEmpty()) {
            return requests.stream()
                    .filter(i -> i.getAddressee().getUsername().equals(username))
                    .findFirst()
                    .map(
                            itm -> UserJsonBulk.fromEntity(userEntity, itm.getStatus() == FriendshipStatus.PENDING
                                    ? INVITE_RECEIVED
                                    : FRIEND)
                    ).orElse(UserJsonBulk.fromEntity(userEntity));
        }
        if (!addresses.isEmpty()) {
            return addresses.stream()
                    .filter(i -> i.getRequester().getUsername().equals(username))
                    .findFirst()
                    .map(
                            itm -> UserJsonBulk.fromEntity(userEntity, itm.getStatus() == FriendshipStatus.PENDING
                                    ? INVITE_SENT
                                    : FRIEND)
                    ).orElse(UserJsonBulk.fromEntity(userEntity));
        }
        return UserJsonBulk.fromEntity(userEntity);
    }
}
