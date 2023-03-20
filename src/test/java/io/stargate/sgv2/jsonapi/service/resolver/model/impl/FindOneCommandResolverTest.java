package io.stargate.sgv2.jsonapi.service.resolver.model.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.Mock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.stargate.sgv2.common.testprofiles.NoGlobalResourcesTestProfile;
import io.stargate.sgv2.jsonapi.api.model.command.CommandContext;
import io.stargate.sgv2.jsonapi.api.model.command.impl.FindOneCommand;
import io.stargate.sgv2.jsonapi.service.operation.model.Operation;
import io.stargate.sgv2.jsonapi.service.operation.model.ReadType;
import io.stargate.sgv2.jsonapi.service.operation.model.impl.DBFilterBase;
import io.stargate.sgv2.jsonapi.service.operation.model.impl.FindOperation;
import io.stargate.sgv2.jsonapi.service.shredding.model.DocumentId;
import javax.inject.Inject;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(NoGlobalResourcesTestProfile.Impl.class)
public class FindOneCommandResolverTest {
  @Inject ObjectMapper objectMapper;
  @Inject FindOneCommandResolver resolver;

  @Nested
  class Resolve {

    @Mock CommandContext commandContext;

    @Test
    public void idFilterCondition() throws Exception {
      String json =
          """
          {
            "findOne": {
              "sort": [
                "user.name",
                "-user.age"
              ],
              "filter" : {"_id" : "id"}
            }
          }
          """;

      FindOneCommand command = objectMapper.readValue(json, FindOneCommand.class);
      Operation operation = resolver.resolveCommand(commandContext, command);

      assertThat(operation)
          .isInstanceOfSatisfying(
              FindOperation.class,
              op -> {
                DBFilterBase.IDFilter filter =
                    new DBFilterBase.IDFilter(
                        DBFilterBase.IDFilter.Operator.EQ, DocumentId.fromString("id"));

                assertThat(op.objectMapper()).isEqualTo(objectMapper);
                assertThat(op.commandContext()).isEqualTo(commandContext);
                assertThat(op.limit()).isEqualTo(1);
                assertThat(op.pageSize()).isEqualTo(1);
                assertThat(op.pagingState()).isNull();
                assertThat(op.readType()).isEqualTo(ReadType.DOCUMENT);
                assertThat(op.filters()).singleElement().isEqualTo(filter);
              });
    }

    @Test
    public void noFilterCondition() throws Exception {
      String json =
          """
          {
            "findOne": {
              "sort": [
                "user.name",
                "-user.age"
              ]
            }
          }
          """;

      FindOneCommand command = objectMapper.readValue(json, FindOneCommand.class);
      Operation operation = resolver.resolveCommand(commandContext, command);

      assertThat(operation)
          .isInstanceOfSatisfying(
              FindOperation.class,
              op -> {
                assertThat(op.objectMapper()).isEqualTo(objectMapper);
                assertThat(op.commandContext()).isEqualTo(commandContext);
                assertThat(op.limit()).isEqualTo(1);
                assertThat(op.pageSize()).isEqualTo(1);
                assertThat(op.pagingState()).isNull();
                assertThat(op.readType()).isEqualTo(ReadType.DOCUMENT);
                assertThat(op.filters()).isEmpty();
              });
    }

    @Test
    public void dynamicFilterCondition() throws Exception {
      String json =
          """
          {
            "findOne": {
              "sort": [
                "user.name",
                "-user.age"
              ],
              "filter" : {"col" : "val"}
            }
          }
          """;

      FindOneCommand command = objectMapper.readValue(json, FindOneCommand.class);
      Operation operation = resolver.resolveCommand(commandContext, command);

      assertThat(operation)
          .isInstanceOfSatisfying(
              FindOperation.class,
              op -> {
                DBFilterBase.TextFilter filter =
                    new DBFilterBase.TextFilter(
                        "col", DBFilterBase.MapFilterBase.Operator.EQ, "val");

                assertThat(op.objectMapper()).isEqualTo(objectMapper);
                assertThat(op.commandContext()).isEqualTo(commandContext);
                assertThat(op.limit()).isEqualTo(1);
                assertThat(op.pageSize()).isEqualTo(1);
                assertThat(op.pagingState()).isNull();
                assertThat(op.readType()).isEqualTo(ReadType.DOCUMENT);
                assertThat(op.filters()).singleElement().isEqualTo(filter);
              });
    }
  }
}
