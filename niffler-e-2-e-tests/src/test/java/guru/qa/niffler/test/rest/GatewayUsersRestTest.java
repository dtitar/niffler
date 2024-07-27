package guru.qa.niffler.test.rest;

import guru.qa.niffler.jupiter.annotation.ApiLogin;
import guru.qa.niffler.jupiter.annotation.Friends;
import guru.qa.niffler.jupiter.annotation.GenerateUser;
import guru.qa.niffler.jupiter.annotation.OutcomeInvitations;
import guru.qa.niffler.jupiter.annotation.Token;
import guru.qa.niffler.jupiter.annotation.User;
import guru.qa.niffler.model.rest.CurrencyValues;
import guru.qa.niffler.model.rest.FriendState;
import guru.qa.niffler.model.rest.UserJson;
import io.qameta.allure.AllureId;
import io.qameta.allure.Epic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static guru.qa.niffler.model.rest.FriendState.FRIEND;
import static guru.qa.niffler.model.rest.FriendState.INVITE_RECEIVED;
import static guru.qa.niffler.model.rest.FriendState.INVITE_SENT;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Epic("[REST][niffler-gateway]: Пользователи")
@DisplayName("[REST][niffler-gateway]: Пользователи")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GatewayUsersRestTest extends BaseRestTest {

    @Test
    @DisplayName("REST: Для нового пользователя должна возвращаться информация из niffler-userdata c дефолтными значениями")
    @AllureId("200022")
    @Tag("REST")
    @ApiLogin(
            user = @GenerateUser()
    )
    void currentUserTest(@User UserJson user,
                         @Token String bearerToken) throws Exception {
        final UserJson currentUserResponse = gatewayApiClient.currentUser(bearerToken);

        step("Check that response contains ID (GUID)", () ->
                assertTrue(currentUserResponse.id().toString().matches(ID_REGEXP))
        );
        step("Check that response contains username", () ->
                assertEquals(user.username(), currentUserResponse.username())
        );
        step("Check that response contains default currency (RUB)", () ->
                assertEquals(CurrencyValues.RUB, currentUserResponse.currency())
        );
    }

    @Test
    @DisplayName("REST: При обновлении юзера должны сохраняться значения в niffler-userdata")
    @AllureId("200023")
    @Tag("REST")
    @ApiLogin(
            user = @GenerateUser()
    )
    void updateUserTest(@User UserJson user,
                        @Token String bearerToken) throws Exception {
        final String firstName = "Pizzly";
        final String secondName = "Pizzlyvich";

        UserJson jsonUser = new UserJson(
                null,
                user.username(),
                firstName + " " + secondName,
                firstName,
                secondName,
                CurrencyValues.KZT,
                null,
                null,
                null,
                null
        );

        final UserJson updateUserInfoResponse = gatewayApiClient.updateUser(bearerToken, jsonUser);

        step("Check that response contains ID (GUID)", () ->
                assertTrue(updateUserInfoResponse.id().toString().matches(ID_REGEXP))
        );
        step("Check that response contains username", () ->
                assertEquals(user.username(), updateUserInfoResponse.username())
        );
        step("Check that response contains updated currency (KZT)", () ->
                assertEquals(CurrencyValues.KZT, updateUserInfoResponse.currency())
        );
        step("Check that response contains updated firstname (Pizzly)", () ->
                assertEquals(firstName, updateUserInfoResponse.firstname())
        );
        step("Check that response contains updated surname (Pizzlyvich)", () ->
                assertEquals(secondName, updateUserInfoResponse.surname())
        );
    }

    @Test
    @DisplayName("REST: Список всех пользователей системы не должен быть пустым" +
            " и содержать друга первым элементом, исходящее предложение дружить - вторым")
    @AllureId("200024")
    @Tag("REST")
    @ApiLogin(
            user = @GenerateUser(
                    friends = @Friends(count = 1),
                    outcomeInvitations = @OutcomeInvitations(count = 1)
            )
    )
    @Order(1)
    void allUsersTest(@User UserJson user,
                      @Token String bearerToken) throws Exception {
        UserJson testFriend = user.testData().friends().getFirst();
        UserJson outcomeInvitation = user.testData().outcomeInvitations().getFirst();

        final List<UserJson> allUsersResponse = gatewayApiClient.allUsers(bearerToken, null);

        step("Check that all users list is not empty", () ->
                assertTrue(allUsersResponse.size() > 2)
        );

        step("Check sorting by status", () -> {
                    assertEquals(FRIEND, allUsersResponse.getFirst().friendState());
                    assertEquals(INVITE_SENT, allUsersResponse.get(1).friendState());
                }
        );

        final var foundedFriend = allUsersResponse.getFirst();
        final var foundedInvitation = allUsersResponse.get(1);

        step("Check friend in response", () -> {
            assertSame(FRIEND, foundedFriend.friendState());
            assertEquals(testFriend.id(), foundedFriend.id());
            assertEquals(testFriend.username(), foundedFriend.username());
        });

        step("Check outcome invitation in response", () -> {
            assertSame(FriendState.INVITE_SENT, foundedInvitation.friendState());
            assertEquals(outcomeInvitation.id(), foundedInvitation.id());
            assertEquals(outcomeInvitation.username(), foundedInvitation.username());
        });
    }
}
