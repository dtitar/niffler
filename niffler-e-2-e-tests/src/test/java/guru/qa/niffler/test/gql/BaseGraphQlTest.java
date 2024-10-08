package guru.qa.niffler.test.gql;

import guru.qa.niffler.api.GatewayGqlApiClient;
import guru.qa.niffler.jupiter.annotation.meta.GqlTest;
import guru.qa.niffler.test.rest.BaseRestTest;

@GqlTest
public abstract class BaseGraphQlTest extends BaseRestTest {

  protected static final GatewayGqlApiClient gqlClient = new GatewayGqlApiClient();
}
