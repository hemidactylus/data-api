package io.stargate.sgv2.jsonapi.service.resolver.model.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.stargate.sgv2.jsonapi.api.model.command.CommandContext;
import io.stargate.sgv2.jsonapi.api.model.command.clause.filter.LogicalExpression;
import io.stargate.sgv2.jsonapi.api.model.command.clause.sort.SortClause;
import io.stargate.sgv2.jsonapi.api.model.command.impl.DeleteOneCommand;
import io.stargate.sgv2.jsonapi.api.request.DataApiRequestInfo;
import io.stargate.sgv2.jsonapi.api.v1.metrics.JsonApiMetricsConfig;
import io.stargate.sgv2.jsonapi.config.OperationsConfig;
import io.stargate.sgv2.jsonapi.service.operation.model.Operation;
import io.stargate.sgv2.jsonapi.service.operation.model.ReadType;
import io.stargate.sgv2.jsonapi.service.operation.model.impl.DeleteOperation;
import io.stargate.sgv2.jsonapi.service.operation.model.impl.FindOperation;
import io.stargate.sgv2.jsonapi.service.projection.DocumentProjector;
import io.stargate.sgv2.jsonapi.service.resolver.model.CommandResolver;
import io.stargate.sgv2.jsonapi.service.resolver.model.impl.matcher.FilterableResolver;
import io.stargate.sgv2.jsonapi.util.SortClauseUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

/**
 * Resolves the {@link DeleteOneCommand } DeleteOne command implements Filterable to identify the
 * record to be deleted, Based on the filter condition a record will deleted
 */
@ApplicationScoped
public class DeleteOneCommandResolver extends FilterableResolver<DeleteOneCommand>
    implements CommandResolver<DeleteOneCommand> {

  private final OperationsConfig operationsConfig;
  private final ObjectMapper objectMapper;

  private final MeterRegistry meterRegistry;
  private final DataApiRequestInfo dataApiRequestInfo;
  private final JsonApiMetricsConfig jsonApiMetricsConfig;

  @Inject
  public DeleteOneCommandResolver(
      OperationsConfig operationsConfig,
      ObjectMapper objectMapper,
      MeterRegistry meterRegistry,
      DataApiRequestInfo dataApiRequestInfo,
      JsonApiMetricsConfig jsonApiMetricsConfig) {
    this.operationsConfig = operationsConfig;
    this.objectMapper = objectMapper;
    this.meterRegistry = meterRegistry;
    this.dataApiRequestInfo = dataApiRequestInfo;
    this.jsonApiMetricsConfig = jsonApiMetricsConfig;
  }

  @Override
  public Operation resolveCommand(CommandContext commandContext, DeleteOneCommand command) {
    FindOperation findOperation = getFindOperation(commandContext, command);
    return DeleteOperation.delete(
        commandContext, findOperation, 1, operationsConfig.lwt().retries());
  }

  @Override
  public Class<DeleteOneCommand> getCommandClass() {
    return DeleteOneCommand.class;
  }

  private FindOperation getFindOperation(CommandContext commandContext, DeleteOneCommand command) {

    LogicalExpression logicalExpression = resolve(commandContext, command);
    final SortClause sortClause = command.sortClause();
    // validate sort path
    if (sortClause != null) {
      sortClause.validate(commandContext);
    }

    float[] vector = SortClauseUtil.resolveVsearch(sortClause);
    addToMetrics(
        meterRegistry,
        dataApiRequestInfo,
        jsonApiMetricsConfig,
        command,
        logicalExpression,
        vector != null);
    if (vector != null) {
      return FindOperation.vsearchSingle(
          commandContext,
          logicalExpression,
          DocumentProjector.includeAllProjector(),
          ReadType.KEY,
          objectMapper,
          vector,
          false);
    }

    List<FindOperation.OrderBy> orderBy = SortClauseUtil.resolveOrderBy(sortClause);
    // If orderBy present
    if (orderBy != null) {
      return FindOperation.sortedSingle(
          commandContext,
          logicalExpression,
          DocumentProjector.includeAllProjector(),
          // For in memory sorting we read more data than needed, so defaultSortPageSize like 100
          operationsConfig.defaultSortPageSize(),
          ReadType.SORTED_DOCUMENT,
          objectMapper,
          orderBy,
          0,
          // For in memory sorting if no limit provided in the request will use
          // documentConfig.defaultPageSize() as limit
          operationsConfig.maxDocumentSortCount(),
          false);
    } else {
      return FindOperation.unsortedSingle(
          commandContext,
          logicalExpression,
          DocumentProjector.includeAllProjector(),
          ReadType.KEY,
          objectMapper,
          false);
    }
  }
}
