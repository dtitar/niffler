package com.github.dtitar.test.web;


import com.github.dtitar.jupiter.annotation.User;
import com.github.dtitar.model.rest.UserJson;
import com.github.dtitar.page.MainPage;
import com.github.dtitar.page.WelcomePage;
import io.qameta.allure.AllureId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.codeborne.selenide.Selenide.open;
import static com.github.dtitar.jupiter.annotation.User.UserType.INVITATION_RECEIVED;
import static com.github.dtitar.jupiter.annotation.User.UserType.INVITATION_SENT;
import static com.github.dtitar.jupiter.annotation.User.UserType.WITH_FRIENDS;

public class FriendsWebTest extends BaseWebTest {

    private MainPage mainPage;

    @BeforeEach
    void doLogin(@User(userType = WITH_FRIENDS) UserJson userForTest) {
        mainPage = open(WelcomePage.URL, WelcomePage.class)
                .doLogin()
                .fillLoginPage(userForTest.getUsername(), userForTest.getPassword())
                .submit(MainPage.class);
    }

    @Test
    @AllureId("101")
    void friendAcceptedInvitationShouldHaveFriendInTableWhoSendInvitation(@User(userType = WITH_FRIENDS) UserJson userForTest) throws InterruptedException {
        mainPage.getHeader()
                .toFriendsPage()
                .checkExistingFriendsCount(1);
    }


    @Test
    @AllureId("102")
    void friendWhoSentInvitationShouldHavePendingInvitationInAllPeopleTable(@User(userType = INVITATION_SENT) UserJson userForTest) throws InterruptedException {
    }

    @Test
    @AllureId("103")
    void friendWhoGotInvitationShouldHaveInvitationInFriendsTable(@User(userType = INVITATION_RECEIVED) UserJson userForTest) {
        mainPage.getHeader()
                .toFriendsPage();
    }
}
