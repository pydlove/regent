<script setup lang="ts">
import { onMounted, ref } from 'vue'

/*
 * 字体探针：不靠肉眼猜「字体对不对」，直接问浏览器到底拿哪套字形渲染。
 *
 * 为什么不用 document.fonts.check()：它对「本机装没装」和「CSS 有没有在用」是两回事，
 *   回答不了「实际用了谁」。
 * 为什么不用 measureText 宽度比对西文：候选之间的宽度差经常小到分不出来。
 * 为什么中文可以用宽度比对：中文字形在绝大多数 CJK 字体里都是 1em 步进（13px 字 → 13.00px）。
 *   凡是步进不是整 13px 的，就一定不是那套中文字体 —— 这一条比指纹更灵敏。
 * 指纹用于「是不是回退到了兜底字体」：用 canvas 把同一串样本分别用候选字体和
 *   一个保证不存在的字体名画出来，比对像素。相同 → 该候选没生效。
 *
 * 一个坑：canvas 的 ctx.font 解析失败时是静默的，会保留上一次的值，
 *   于是所有候选指纹全都一样，看上去像「全都不生效」。所以每次赋值后回读 ctx.font 校验。
 */

const CANDIDATES = [
  'system-ui',
  '-apple-system',
  'BlinkMacSystemFont',
  'SF Pro Text',
  'Helvetica Neue',
  'Helvetica',
  'Arial',
  'PingFang SC',
  'Hiragino Sans GB',
  'Heiti SC',
  'Songti SC',
]

const LATIN = 'Regent L3 1025'
const CJK = '修正登录页空指针'
const MISSING = '__regent_absent_font__'
const SIZE = 13

type Row = {
  family: string
  applied: string
  parsed: boolean
  latin: number
  cjk: number
  fallback: boolean
  matchBody: boolean
}

const rows = ref<Row[]>([])
const bodyStack = ref('')
const bodySpec = ref('')
const parsed = ref(true)
const latinWinner = ref('')
const cjkWinner = ref('')

const quote = (family: string) => (/^[a-z-]+$/i.test(family) ? family : `"${family}"`)

function context(spec: string) {
  const canvas = document.createElement('canvas')
  canvas.width = 260
  canvas.height = 44
  const ctx = canvas.getContext('2d')!
  ctx.font = spec
  return { ctx, applied: ctx.font }
}

function fingerprint(font: string, sample: string) {
  const { ctx, applied } = context(font)
  ctx.textBaseline = 'alphabetic'
  ctx.fillStyle = '#000'
  ctx.fillText(sample, 4, 32)
  const { data } = ctx.getImageData(0, 0, 260, 44)
  let hash = 5381
  for (let i = 3; i < data.length; i += 4) hash = ((hash * 33) ^ data[i]) >>> 0
  return { hash, applied }
}

const round2 = (n: number) => Math.round(n * 100) / 100

onMounted(() => {
  const family = getComputedStyle(document.body).fontFamily
  bodyStack.value = family
  const spec = `${SIZE}px ${family}`
  bodySpec.value = spec

  const probe = context(spec)
  parsed.value = probe.applied.replace(/\s+/g, '') === spec.replace(/\s+/g, '')

  const missing = fingerprint(`${SIZE}px ${MISSING}`, LATIN).hash
  const missingCjk = fingerprint(`${SIZE}px ${MISSING}`, CJK).hash
  const bodyLatin = fingerprint(spec, LATIN).hash
  const bodyCjk = fingerprint(spec, CJK).hash

  // 中文步进的基准：把 body 那串栈换成一个确定存在的西文字体，
  // 中文必然走兜底 —— 兜底用的是谁，就是「中文实际命中」。
  const cjkFallbackAdvance = round2(
    context(`${SIZE}px ${MISSING}`).ctx.measureText(CJK).width / CJK.length,
  )

  rows.value = CANDIDATES.map((candidate) => {
    const one = fingerprint(`${SIZE}px ${quote(candidate)}`, LATIN)
    const two = fingerprint(`${SIZE}px ${quote(candidate)}`, CJK)
    return {
      family: candidate,
      applied: one.applied,
      parsed: one.applied.replace(/\s+/g, '') === `${SIZE}px ${quote(candidate)}`.replace(/\s+/g, ''),
      latin: 0,
      cjk: 0,
      fallback: one.hash === missing && two.hash === missingCjk,
      matchBody: one.hash === bodyLatin && two.hash === bodyCjk,
    }
  })

  // 宽度单独量一遍：指纹那次调用已经画过图，ctx 还在但别复用，重新建更省心
  rows.value = rows.value.map((row) => {
    const { ctx } = context(`${SIZE}px ${quote(row.family)}`)
    return {
      ...row,
      latin: round2(ctx.measureText(LATIN).width),
      cjk: round2(ctx.measureText(CJK).width / CJK.length),
    }
  })

  latinWinner.value = rows.value.find((r) => r.matchBody && !r.fallback)?.family ?? '未命中'
  cjkWinner.value =
    rows.value.find((r) => r.cjk === cjkFallbackAdvance && !r.fallback)?.family ?? '未命中'
})
</script>

<template>
  <div class="space-y-3">
    <div class="space-y-0.5 text-caption">
      <p class="text-label-secondary">
        body 计算值：<code class="text-label">{{ bodyStack }}</code>
      </p>
      <p class="text-label-secondary">
        canvas 回读：<code class="text-label">{{ bodySpec }}</code>
        <span :class="parsed ? 'text-success' : 'text-danger'">
          {{ parsed ? '· 解析正常' : '· 解析失败，下面的指纹不可信' }}
        </span>
      </p>
      <p class="text-label-secondary">
        西文命中：<span class="text-label">{{ latinWinner }}</span>
        ｜中文命中：<span class="text-label">{{ cjkWinner }}</span>
      </p>
    </div>

    <table class="w-full text-caption">
      <thead>
        <tr class="text-label-tertiary">
          <th class="pb-1 text-left font-normal">候选字体</th>
          <th class="pb-1 text-right font-normal">13px 西文宽</th>
          <th class="pb-1 text-right font-normal">13px 中文步进</th>
          <th class="pb-1 text-right font-normal">= 兜底字体</th>
          <th class="pb-1 text-right font-normal">= body 实测</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row.family" class="border-t border-separator-soft">
          <td class="py-0.5 text-label">
            {{ row.family }}
            <span v-if="!row.parsed" class="text-danger">· 未解析</span>
          </td>
          <td class="tnum py-0.5 text-right text-label-secondary">{{ row.latin }}</td>
          <td class="tnum py-0.5 text-right text-label-secondary">{{ row.cjk }}</td>
          <td class="py-0.5 text-right text-label-tertiary">{{ row.fallback ? '是' : '—' }}</td>
          <td class="py-0.5 text-right">
            <span :class="row.matchBody ? 'text-accent' : 'text-label-tertiary'">
              {{ row.matchBody ? '● 是' : '—' }}
            </span>
          </td>
        </tr>
      </tbody>
    </table>
    <p class="text-caption text-label-tertiary">
      判读法：中文步进是整 13px 的候选才可能是真正在用的中文字体；若所有候选都不等于 body，说明
      中文由 Chrome 自己的 fallback 链给出，CSS 里写的字体名一个都没生效。
    </p>
  </div>
</template>
