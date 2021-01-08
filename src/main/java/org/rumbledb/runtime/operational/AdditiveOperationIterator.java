/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: Stefan Irimescu, Can Berker Cikis
 *
 */

package org.rumbledb.runtime.operational;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;

import org.joda.time.DateTime;
import org.joda.time.Period;
import org.joda.time.PeriodType;
import org.rumbledb.api.Item;
import org.rumbledb.context.DynamicContext;
import org.rumbledb.exceptions.ExceptionMetadata;
import org.rumbledb.exceptions.IteratorFlowException;
import org.rumbledb.exceptions.MoreThanOneItemException;
import org.rumbledb.exceptions.UnexpectedTypeException;
import org.rumbledb.expressions.ExecutionMode;
import org.rumbledb.items.ItemFactory;
import org.rumbledb.runtime.LocalRuntimeIterator;
import org.rumbledb.runtime.RuntimeIterator;
import org.rumbledb.runtime.operational.base.ComparisonUtil;


public class AdditiveOperationIterator extends LocalRuntimeIterator {

    private static final long serialVersionUID = 1L;

    private Item left;
    private Item right;
    private boolean isMinus;
    private RuntimeIterator leftIterator;
    private RuntimeIterator rightIterator;

    public AdditiveOperationIterator(
            RuntimeIterator leftIterator,
            RuntimeIterator rightIterator,
            boolean isMinus,
            ExecutionMode executionMode,
            ExceptionMetadata iteratorMetadata
    ) {
        super(Arrays.asList(leftIterator, rightIterator), executionMode, iteratorMetadata);
        this.leftIterator = leftIterator;
        this.rightIterator = rightIterator;
        this.isMinus = isMinus;
    }

    @Override
    public Item next() {
        if (!this.hasNext) {
            throw new IteratorFlowException(RuntimeIterator.FLOW_EXCEPTION_MESSAGE, getMetadata());
        }
        this.hasNext = false;
        Item result = processItem(this.left, this.right, this.isMinus);
        if (result == null) {
            throw new UnexpectedTypeException(
                    " \"+\": operation not possible with parameters of type \""
                        + this.left.getDynamicType().toString()
                        + "\" and \""
                        + this.right.getDynamicType().toString()
                        + "\"",
                    getMetadata()
            );
        }
        return result;
    }

    @Override
    public void open(DynamicContext context) {
        super.open(context);

        try {
            this.left = this.leftIterator.materializeAtMostOneItemOrNull(this.currentDynamicContextForLocalExecution);
        } catch (MoreThanOneItemException e) {
            throw new UnexpectedTypeException(
                    "Addition expression requires at most one item in its left input sequence.",
                    getMetadata()
            );
        }
        try {
            this.right = this.rightIterator.materializeAtMostOneItemOrNull(this.currentDynamicContextForLocalExecution);
        } catch (MoreThanOneItemException e) {
            throw new UnexpectedTypeException(
                    "Addition expression requires at most one item in its right input sequence.",
                    getMetadata()
            );
        }

        // if left or right equals empty sequence, return empty sequence
        if (this.left == null || this.right == null) {
            this.hasNext = false;
        } else {
            ComparisonUtil.checkBinaryOperation(
                this.left,
                this.right,
                this.isMinus ? "-" : "+",
                getMetadata()
            );
            this.hasNext = true;
        }
    }

    public static Item processItem(
            Item left,
            Item right,
            boolean isMinus
    ) {
        if (
            left.isInt()
                && right.isInt()
        ) {
            if (
                right.isInt()
                    && (left.getIntValue() < Integer.MAX_VALUE / 2
                        && left.getIntValue() > -Integer.MAX_VALUE / 2
                        && right.getIntValue() > -Integer.MAX_VALUE / 2
                        && right.getIntValue() < Integer.MAX_VALUE / 2)
            ) {
                return processInt(left.getIntValue(), right.getIntValue(), isMinus);
            }
        }

        // General cases
        if (left.isDouble() && right.isNumeric()) {
            double l = left.getDoubleValue();
            double r = 0;
            if (right.isDouble()) {
                r = right.getDoubleValue();
            } else {
                r = right.castToDoubleValue();
            }
            return processDouble(l, r, isMinus);
        }
        if (right.isDouble() && left.isNumeric()) {
            double l = left.castToDoubleValue();
            double r = right.getDoubleValue();
            return processDouble(l, r, isMinus);
        }
        if (left.isFloat() && right.isNumeric()) {
            float l = left.getFloatValue();
            float r = 0;
            if (right.isFloat()) {
                r = right.getFloatValue();
            } else {
                r = right.castToFloatValue();
            }
            return processFloat(l, r, isMinus);
        }
        if (right.isFloat() && left.isNumeric()) {
            float l = left.castToFloatValue();
            float r = right.getFloatValue();
            return processFloat(l, r, isMinus);
        }
        if (left.isInteger() && right.isInteger()) {
            BigInteger l = left.getIntegerValue();
            BigInteger r = right.getIntegerValue();
            return processInteger(l, r, isMinus);
        }
        if (left.isDecimal() && right.isDecimal()) {
            BigDecimal l = left.getDecimalValue();
            BigDecimal r = right.getDecimalValue();
            return processDecimal(l, r, isMinus);
        }
        if (left.isYearMonthDuration() && right.isYearMonthDuration()) {
            Period l = left.getDurationValue();
            Period r = right.getDurationValue();
            return processYearMonthDuration(l, r, isMinus);
        }
        if (left.isDayTimeDuration() && right.isDayTimeDuration()) {
            Period l = left.getDurationValue();
            Period r = right.getDurationValue();
            return processDayTimeDuration(l, r, isMinus);
        }
        if (left.isDate() && right.isYearMonthDuration()) {
            DateTime l = left.getDateTimeValue();
            Period r = right.getDurationValue();
            return processDateTimeDurationDate(l, r, isMinus, left.hasTimeZone());
        }
        if (left.isDate() && right.isDayTimeDuration()) {
            DateTime l = left.getDateTimeValue();
            Period r = right.getDurationValue();
            return processDateTimeDurationDate(l, r, isMinus, left.hasTimeZone());
        }
        if (left.isYearMonthDuration() && right.isDate()) {
            if (!isMinus) {
                Period l = left.getDurationValue();
                DateTime r = right.getDateTimeValue();
                return processDateTimeDurationDate(r, l, isMinus, right.hasTimeZone());
            }
        }
        if (left.isDayTimeDuration() && right.isDate()) {
            if (!isMinus) {
                Period l = left.getDurationValue();
                DateTime r = right.getDateTimeValue();
                return processDateTimeDurationDate(r, l, isMinus, right.hasTimeZone());
            }
        }
        if (left.isTime() && right.isDayTimeDuration()) {
            DateTime l = left.getDateTimeValue();
            Period r = right.getDurationValue();
            return processDateTimeDurationTime(l, r, isMinus, left.hasTimeZone());
        }
        if (left.isDayTimeDuration() && right.isTime()) {
            if (!isMinus) {
                Period l = left.getDurationValue();
                DateTime r = right.getDateTimeValue();
                return processDateTimeDurationTime(r, l, isMinus, right.hasTimeZone());
            }
        }
        if (left.isDateTime() && right.isYearMonthDuration()) {
            DateTime l = left.getDateTimeValue();
            Period r = right.getDurationValue();
            return processDateTimeDurationDateTime(l, r, isMinus, left.hasTimeZone());
        }
        if (left.isDateTime() && right.isDayTimeDuration()) {
            DateTime l = left.getDateTimeValue();
            Period r = right.getDurationValue();
            return processDateTimeDurationDateTime(l, r, isMinus, left.hasTimeZone());
        }
        if (left.isYearMonthDuration() && right.isDateTime()) {
            if (!isMinus) {
                Period l = left.getDurationValue();
                DateTime r = right.getDateTimeValue();
                return processDateTimeDurationDateTime(r, l, isMinus, right.hasTimeZone());
            }
        }
        if (left.isDayTimeDuration() && right.isDateTime()) {
            if (!isMinus) {
                Period l = left.getDurationValue();
                DateTime r = right.getDateTimeValue();
                return processDateTimeDurationDateTime(r, l, isMinus, right.hasTimeZone());
            }
        }
        if (left.isDate() && right.isDate()) {
            if (isMinus) {
                DateTime l = left.getDateTimeValue();
                DateTime r = right.getDateTimeValue();
                return processDateTimeDayTime(l, r);
            }
        }
        if (left.isTime() && right.isTime()) {
            if (isMinus) {
                DateTime l = left.getDateTimeValue();
                DateTime r = right.getDateTimeValue();
                return processDateTimeDayTime(l, r);
            }
        }
        if (left.isDateTime() && right.isDateTime()) {
            if (isMinus) {
                DateTime l = left.getDateTimeValue();
                DateTime r = right.getDateTimeValue();
                return processDateTimeDayTime(l, r);
            }
        }
        return null;
    }

    private static Item processDouble(
            double l,
            double r,
            boolean isMinus
    ) {
        if (isMinus) {
            return ItemFactory.getInstance().createDoubleItem(l - r);
        } else {
            return ItemFactory.getInstance().createDoubleItem(l + r);
        }
    }

    private static Item processFloat(
            float l,
            float r,
            boolean isMinus
    ) {
        if (isMinus) {
            return ItemFactory.getInstance().createFloatItem(l - r);
        } else {
            return ItemFactory.getInstance().createFloatItem(l + r);
        }
    }

    private static Item processDecimal(
            BigDecimal l,
            BigDecimal r,
            boolean isMinus
    ) {
        if (isMinus) {
            return ItemFactory.getInstance().createDecimalItem(l.subtract(r));
        } else {
            return ItemFactory.getInstance().createDecimalItem(l.add(r));
        }
    }

    private static Item processInteger(
            BigInteger l,
            BigInteger r,
            boolean isMinus
    ) {
        if (isMinus) {
            return ItemFactory.getInstance().createIntegerItem(l.subtract(r));
        } else {
            return ItemFactory.getInstance().createIntegerItem(l.add(r));
        }
    }

    private static Item processInt(
            int l,
            int r,
            boolean isMinus
    ) {
        if (isMinus) {
            return ItemFactory.getInstance().createIntItem(l - r);
        } else {
            return ItemFactory.getInstance().createIntItem(l + r);
        }
    }

    private static Item processYearMonthDuration(
            Period l,
            Period r,
            boolean isMinus
    ) {
        if (isMinus) {
            return ItemFactory.getInstance().createYearMonthDurationItem(l.minus(r));
        } else {
            return ItemFactory.getInstance().createYearMonthDurationItem(l.plus(r));
        }
    }

    private static Item processDayTimeDuration(
            Period l,
            Period r,
            boolean isMinus
    ) {
        if (isMinus) {
            return ItemFactory.getInstance().createDayTimeDurationItem(l.minus(r));
        } else {
            return ItemFactory.getInstance().createDayTimeDurationItem(l.plus(r));
        }
    }

    private static Item processDateTimeDayTime(
            DateTime l,
            DateTime r
    ) {
        return ItemFactory.getInstance()
            .createDayTimeDurationItem(new Period(r, l, PeriodType.dayTime()));
    }

    private static Item processDateTimeDurationDate(
            DateTime l,
            Period r,
            boolean isMinus,
            boolean timeZone
    ) {
        if (isMinus) {
            return ItemFactory.getInstance()
                .createDateItem(l.minus(r), timeZone);
        } else {
            return ItemFactory.getInstance()
                .createDateItem(l.plus(r), timeZone);
        }
    }

    private static Item processDateTimeDurationTime(
            DateTime l,
            Period r,
            boolean isMinus,
            boolean timeZone
    ) {
        if (isMinus) {
            return ItemFactory.getInstance()
                .createTimeItem(l.minus(r), timeZone);
        } else {
            return ItemFactory.getInstance()
                .createTimeItem(l.plus(r), timeZone);
        }
    }

    private static Item processDateTimeDurationDateTime(
            DateTime l,
            Period r,
            boolean isMinus,
            boolean timeZone
    ) {
        if (isMinus) {
            return ItemFactory.getInstance()
                .createDateTimeItem(l.minus(r), timeZone);
        } else {
            return ItemFactory.getInstance()
                .createDateTimeItem(l.plus(r), timeZone);
        }
    }
}
