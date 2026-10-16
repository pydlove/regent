package com.pydlove.regent.shared.kernel;

import com.pydlove.regent.shared.domain.DomainException;

/**
 * 一次执行的 token 消耗。
 *
 * <p>三个分量分开存，是因为它们的价钱差一个数量级 —— 只留一个总数，事后想回答
 * 「这次跑为什么这么贵」就无从下手。总量口径见 {@link #total()}。
 *
 * <p>注意 {@code input} 与 {@code cachedInput} 的口径：codex 的 {@code input_tokens}
 * 本身已包含被缓存的部分，本类按 {@code task_run} 三个列各自的语义独立累加，
 * 与 {@code TaskMapper} 里 {@code token_input + token_cached_input + token_output} 的求和
 * 保持一致。要精确的计费口径得另起一列，不是在这里偷改语义能解决的。
 */
public record TokenUsage(long input, long cachedInput, long output) {

    public static final TokenUsage ZERO = new TokenUsage(0, 0, 0);

    public TokenUsage {
        requireNonNegative(input, "输入 token");
        requireNonNegative(cachedInput, "缓存输入 token");
        requireNonNegative(output, "输出 token");
    }

    public static TokenUsage of(long input, long cachedInput, long output) {
        return new TokenUsage(input, cachedInput, output);
    }

    public long total() {
        return input + cachedInput + output;
    }

    public TokenUsage plus(TokenUsage other) {
        return new TokenUsage(
                input + other.input,
                cachedInput + other.cachedInput,
                output + other.output);
    }

    private static void requireNonNegative(long value, String field) {
        if (value < 0) {
            throw new DomainException(field + "不能为负：" + value);
        }
    }
}
