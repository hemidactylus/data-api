package io.stargate.sgv2.jsonapi.service.resolver.model.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.Mock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.stargate.sgv2.common.testprofiles.NoGlobalResourcesTestProfile;
import io.stargate.sgv2.jsonapi.api.model.command.CommandContext;
import io.stargate.sgv2.jsonapi.api.model.command.impl.InsertOneCommand;
import io.stargate.sgv2.jsonapi.exception.ErrorCode;
import io.stargate.sgv2.jsonapi.exception.JsonApiException;
import io.stargate.sgv2.jsonapi.service.operation.model.Operation;
import io.stargate.sgv2.jsonapi.service.operation.model.impl.InsertOperation;
import io.stargate.sgv2.jsonapi.service.shredding.Shredder;
import io.stargate.sgv2.jsonapi.service.shredding.model.WritableShreddedDocument;
import javax.inject.Inject;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(NoGlobalResourcesTestProfile.Impl.class)
class InsertOneCommandResolverTest {

  @Inject ObjectMapper objectMapper;
  @Inject InsertOneCommandResolver resolver;
  @Inject Shredder shredder;

  @Nested
  class ResolveCommand {

    @Mock CommandContext commandContext;

    @Test
    public void happyPath() throws Exception {
      String json =
          """
          {
            "insertOne": {
              "document" : {
                "_id": "1"
              }
            }
          }
          """;

      InsertOneCommand command = objectMapper.readValue(json, InsertOneCommand.class);
      Operation result = resolver.resolveCommand(commandContext, command);

      assertThat(result)
          .isInstanceOfSatisfying(
              InsertOperation.class,
              op -> {
                WritableShreddedDocument expected = shredder.shred(command.document());

                assertThat(op.commandContext()).isEqualTo(commandContext);
                assertThat(op.ordered()).isTrue();
                assertThat(op.documents()).singleElement().isEqualTo(expected);
              });
    }

    @Test
    public void shredderFailure() throws Exception {
      String json =
          """
          {
            "insertOne": {
              "document" : null
            }
          }
          """;

      InsertOneCommand command = objectMapper.readValue(json, InsertOneCommand.class);
      Throwable failure = catchThrowable(() -> resolver.resolveCommand(commandContext, command));

      assertThat(failure)
          .isInstanceOf(JsonApiException.class)
          .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SHRED_BAD_DOCUMENT_TYPE);
    }
  }
}
