package io.stargate.sgv2.jsonapi.service.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.stargate.sgv2.common.testprofiles.NoGlobalResourcesTestProfile;
import io.stargate.sgv2.jsonapi.exception.ErrorCode;
import io.stargate.sgv2.jsonapi.exception.JsonApiException;
import javax.inject.Inject;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(NoGlobalResourcesTestProfile.Impl.class)
public class DocumentProjectorTest {
  @Inject ObjectMapper objectMapper;

  // Tests for validating issues with Projection definitions
  @Nested
  class ProjectorDefValidation {
    @Test
    public void verifyProjectionJsonObject() throws Exception {
      JsonNode def = objectMapper.readTree(" [ 1, 2, 3 ]");
      Throwable t = catchThrowable(() -> DocumentProjector.createFromDefinition(def));
      assertThat(t)
          .isInstanceOf(JsonApiException.class)
          .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNSUPPORTED_PROJECTION_PARAM)
          .hasMessage("Unsupported projection parameter: definition must be OBJECT, was ARRAY");
    }

    @Test
    public void verifyNoIncludeAfterExclude() throws Exception {
      JsonNode def =
          objectMapper.readTree(
              """
              { "excludeMe" : 0,
                "excludeMeToo" : 0,
                "include.me" : 1
              }
              """);
      Throwable t = catchThrowable(() -> DocumentProjector.createFromDefinition(def));
      assertThat(t)
          .isInstanceOf(JsonApiException.class)
          .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNSUPPORTED_PROJECTION_PARAM)
          .hasMessage(
              "Unsupported projection parameter: cannot include 'include.me' on exclusion projection");
    }

    @Test
    public void verifyNoPathOverlap() throws Exception {
      JsonNode def =
          objectMapper.readTree(
              """
                      { "branch" : 1,
                        "branch.x.leaf" : 1,
                        "include.me" : 1
                      }
                      """);
      Throwable t = catchThrowable(() -> DocumentProjector.createFromDefinition(def));
      assertThat(t)
          .isInstanceOf(JsonApiException.class)
          .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNSUPPORTED_PROJECTION_PARAM)
          .hasMessage(
              "Unsupported projection parameter: projection path conflict between 'branch' and 'branch.x.leaf'");

      // Should be caught regardless of ordering (longer vs shorter path first)
      JsonNode def2 =
          objectMapper.readTree(
              """
                      { "a.y.leaf" : 1,
                        "a" : 1,
                        "value" : 1
                      }
                      """);
      Throwable t2 = catchThrowable(() -> DocumentProjector.createFromDefinition(def2));
      assertThat(t2)
          .isInstanceOf(JsonApiException.class)
          .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNSUPPORTED_PROJECTION_PARAM)
          .hasMessage(
              "Unsupported projection parameter: projection path conflict between 'a' and 'a.y.leaf'");
    }

    @Test
    public void verifyNoExcludeAfterInclude() throws Exception {
      JsonNode def =
          objectMapper.readTree(
              """
              { "includeMe" : 1,
                "misc" : {
                   "nested": {
                     "do" : true,
                     "dont" : false
                    }
                },
                "includeMe2" : 1
              }
              """);
      Throwable t = catchThrowable(() -> DocumentProjector.createFromDefinition(def));
      assertThat(t)
          .isInstanceOf(JsonApiException.class)
          .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNSUPPORTED_PROJECTION_PARAM)
          .hasMessage(
              "Unsupported projection parameter: cannot exclude 'misc.nested.dont' on inclusion projection");
    }

    @Test
    public void verifyProjectionEquality() throws Exception {
      String defStr1 = "{ \"field1\" : 1, \"field2\": 1 }";
      String defStr2 = "{ \"field1\" : 0, \"field2\": 0 }";

      DocumentProjector proj1 =
          DocumentProjector.createFromDefinition(objectMapper.readTree(defStr1));
      assertThat(proj1.isInclusion()).isTrue();
      DocumentProjector proj2 =
          DocumentProjector.createFromDefinition(objectMapper.readTree(defStr2));
      assertThat(proj2.isInclusion()).isFalse();

      // First, verify equality of identical definitions
      assertThat(proj1)
          .isEqualTo(DocumentProjector.createFromDefinition(objectMapper.readTree(defStr1)));
      assertThat(proj2)
          .isEqualTo(DocumentProjector.createFromDefinition(objectMapper.readTree(defStr2)));

      // Then inequality
      assertThat(proj1)
          .isNotEqualTo(DocumentProjector.createFromDefinition(objectMapper.readTree(defStr2)));
      assertThat(proj2)
          .isNotEqualTo(DocumentProjector.createFromDefinition(objectMapper.readTree(defStr1)));
    }
  }
}