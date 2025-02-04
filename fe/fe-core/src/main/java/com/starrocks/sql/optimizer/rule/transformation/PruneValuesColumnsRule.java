// This file is licensed under the Elastic License 2.0. Copyright 2021-present, StarRocks Limited.

package com.starrocks.sql.optimizer.rule.transformation;

import com.google.common.collect.Lists;
import com.starrocks.sql.optimizer.OptExpression;
import com.starrocks.sql.optimizer.OptimizerContext;
import com.starrocks.sql.optimizer.base.ColumnRefSet;
import com.starrocks.sql.optimizer.operator.OperatorType;
import com.starrocks.sql.optimizer.operator.logical.LogicalValuesOperator;
import com.starrocks.sql.optimizer.operator.pattern.Pattern;
import com.starrocks.sql.optimizer.operator.scalar.ColumnRefOperator;
import com.starrocks.sql.optimizer.operator.scalar.ScalarOperator;
import com.starrocks.sql.optimizer.rule.RuleType;

import java.util.Collections;
import java.util.List;

/**
 * Prune columns for logical values operator
 * For: select a from (select 1 as a, 2 as b) t
 * Before: VALUES (1,2)
 * After: VALUES (1)
 */
public class PruneValuesColumnsRule extends TransformationRule {
    public PruneValuesColumnsRule() {
        super(RuleType.TF_PRUNE_VALUES_COLUMNS, Pattern.create(OperatorType.LOGICAL_VALUES));
    }

    @Override
    public List<OptExpression> transform(OptExpression input, OptimizerContext context) {
        ColumnRefSet requiredOutputColumns = context.getTaskContext().get(0).getRequiredColumns();

        LogicalValuesOperator valuesOperator = (LogicalValuesOperator) input.getOp();

        List<Integer> keptIndexes = Lists.newArrayList();
        List<ColumnRefOperator> columnRefOperators = valuesOperator.getColumnRefSet();
        for (int index = 0; index < columnRefOperators.size(); ++index) {
            if (requiredOutputColumns.contains(columnRefOperators.get(index))) {
                keptIndexes.add(index);
            }
        }

        // Need prune columns
        if (keptIndexes.size() != columnRefOperators.size()) {

            /*
             * The LogicalValuesOperator maintain the same prune logic as scannode, at least one column is reserved.
             * To ensure that there is no empty node. Ensure the correctness of the exists predicate
             */
            if (keptIndexes.isEmpty()) {
                if (columnRefOperators.size() == 1) {
                    return Collections.emptyList();
                } else {
                    keptIndexes.add(0);
                }
            }

            List<ColumnRefOperator> newColumnRefs = Lists.newArrayList();

            List<List<ScalarOperator>> newRows = Lists.newArrayList();
            for (int rowId = 0; rowId < valuesOperator.getRows().size(); rowId++) {
                newRows.add(Lists.newArrayList());
            }

            for (int index : keptIndexes) {
                newColumnRefs.add(columnRefOperators.get(index));
                for (int rowId = 0; rowId < newRows.size(); rowId++) {
                    newRows.get(rowId).add(valuesOperator.getRows().get(rowId).get(index));
                }
            }
            LogicalValuesOperator newValuesOperator = new LogicalValuesOperator(newColumnRefs, newRows);
            return Lists.newArrayList(OptExpression.create(newValuesOperator));
        }
        return Collections.emptyList();
    }
}
