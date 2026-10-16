package com.pydlove.regent.shared.kernel;

import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecisionLevelTest {

    @Test
    @DisplayName("higherOf 取更高等级，且与调用方向无关")
    void higherOfTakesTheHigherOneEitherWay() {
        assertEquals(DecisionLevel.L3, DecisionLevel.L1.higherOf(DecisionLevel.L3));
        assertEquals(DecisionLevel.L3, DecisionLevel.L3.higherOf(DecisionLevel.L1));
    }

    @Test
    @DisplayName("higherOf 永远不会降低等级 —— 这是仲裁只升不降的地基")
    void higherOfNeverLowers() {
        assertEquals(DecisionLevel.L3, DecisionLevel.L3.higherOf(DecisionLevel.L0));
        assertEquals(DecisionLevel.L2, DecisionLevel.L2.higherOf(DecisionLevel.L2));
    }

    @Test
    @DisplayName("isWithin 含边界：阈值本身可以自动决策")
    void isWithinIncludesBoundary() {
        assertTrue(DecisionLevel.L1.isWithin(DecisionLevel.L1));
        assertFalse(DecisionLevel.L2.isWithin(DecisionLevel.L1));
    }

    @Test
    @DisplayName("isHigherThan 严格大于，相等时为假")
    void isHigherThanIsStrict() {
        assertTrue(DecisionLevel.L2.isHigherThan(DecisionLevel.L1));
        assertFalse(DecisionLevel.L2.isHigherThan(DecisionLevel.L2));
    }

    @Test
    @DisplayName("parse 同时接受 L2 与 2 两种写法，且容忍大小写与空白")
    void parseAcceptsBothForms() {
        assertEquals(DecisionLevel.L2, DecisionLevel.parse("L2"));
        assertEquals(DecisionLevel.L2, DecisionLevel.parse("l2"));
        assertEquals(DecisionLevel.L2, DecisionLevel.parse("  2  "));
        assertEquals(DecisionLevel.L0, DecisionLevel.parse("0"));
    }

    @Test
    @DisplayName("parse 对空值与未知等级一律抛异常，不猜测")
    void parseRejectsInvalidInput() {
        assertThrows(DomainException.class, () -> DecisionLevel.parse(null));
        assertThrows(DomainException.class, () -> DecisionLevel.parse("   "));
        assertThrows(DomainException.class, () -> DecisionLevel.parse("L9"));
        assertThrows(DomainException.class, () -> DecisionLevel.parse("HIGH"));
    }
}
