# Regent 前端 UI 定义

> 版本：v1.1
> 定义日期：2026-10-16
> 视觉目标：**对标 Apple macOS 原生应用观感**
> 配套文档：`TECH_STACK.md`、`ARCHITECTURE.md`、`AGENTS.md`
>
> v1.1 相对 v1.0 的补强：依赖版本已逐个 `npm view` 核验；补上「二十多个组件怎么保证长成一个样」
> 的那一层——深浅模式落地机制、组件变体系统、间距刻度、焦点环、表格规格，以及一份可直接
> 落地的完整 `tokens.css`。v1.0 只回答了「观感长什么样」，v1.1 回答「靠什么纪律长期不腐烂」。

---

## 一、先说清一件事：没有现成的 macOS 组件库

我先把结论摆出来，避免走弯路：**npm 上不存在能直接上生产的 macOS 风格组件库。**

- Element Plus / TDesign / Naive UI / antd 的默认视觉是通用企业风或 Material，不是 macOS。
- 号称「macOS 复刻」的开源套件，要么早已停维护，要么只是静态截图拼的 demo，要么样式写死在组件内部改不动。

所以正确的路径是：**headless 原语组件（只管行为与无障碍）+ Tailwind + 自建 macOS 设计令牌**。

好处是观感完全可控，且不会因为组件库大版本升级而视觉回退——这对一个要长期运行、由 Codex 反复改动的内部平台很重要。
代价是要多写一层基础组件封装，约 25 个（清单见第六节）。这个成本是划算的，因为 macOS 观感恰恰藏在细节里，而细节只有自己写才控制得住。

**目标运行载体（本节唯一需要你拍板的事）**：目标是**浏览器里的 Web 应用**，macOS 上的
Safari / Chrome 为基准平台，不做 Electron/Tauri 桌面壳。三个理由：平台本身是常驻服务，
你多数时候在手机上批决策、在浏览器里看工作台，桌面壳收益有限；`backdrop-filter` 与
`-apple-system` 在 macOS 浏览器上能力齐全，材质观感不打折；少一层壳就少一套打包与升级负担。

代价必须提前说清：`-apple-system` 只在 macOS 上才解析成真正的 SF Pro，Windows / Linux 上
会退化成备用字体，观感会明显不同。**接受这个降级，但不为它写第二套设计**——不打包 webfont
冒充 SF，也不为 Windows 单独调字号，否则维护成本翻倍且基准被稀释。若将来确实要桌面化，
再加 Tauri 壳即可，本文档的令牌与组件层不需要动。

---

## 二、技术栈

| 层次 | 选型 | 版本 | 说明 |
|---|---|---|---|
| 框架 | Vue | `3.5.44` | 按你此前明确的倾向选 Vue |
| 构建 | Vite | `8.3.4` | |
| 语言 | TypeScript | `6.0.3` | 实测回退：TS 7 起不再导出 `./lib/tsc`，`vue-tsc` 无法工作 |
| 样式 | Tailwind CSS | `4.3.3` | 配合 `@tailwindcss/vite` `4.3.3` |
| 状态 | Pinia | `4.1.0` | |
| 路由 | vue-router | `5.4.0` | |
| 无样式原语 | reka-ui | `2.11.0` | Radix 的 Vue 版，负责弹层/菜单/焦点管理/无障碍 |
| 变体工具 | tailwind-variants | `3.3.1` | 统一 `size` / `variant` 变体，见第五节 |
| 表格 | @tanstack/vue-table | `9.3.1` | 只给逻辑，样式自己写，正好适合 macOS 表格 |
| 图标 | @lucide/vue | `1.57.0` | 1.5px 线宽，最接近 SF Symbols 的中立选择（旧的 `lucide-vue-next` 已废弃） |
| 请求 | 原生 `fetch` 薄封装 | — | 不需要 axios |

> 以上版本已于 2026-10-16 逐个 `npm view` 核验并在脚手架阶段实测（`regent-web/` 已可构建）。
> 核验之外的两处修正记录在第十三节：TS 回退到 6.0.3、图标包换成 `@lucide/vue`。

**为什么不用 Element Plus**：它的视觉是通用企业风，要改成 macOS 得覆盖数百条 CSS 变量，改到最后是在跟它打架，而且它每次升级都会把观感拉回去。headless + Tailwind 反而是更短的路。

---

## 三、macOS 设计令牌（本文档的核心）

令牌集中在 `src/styles/tokens.css`，用 Tailwind v4 的 `@theme` 声明，业务组件一律引用令牌、不写裸值。

### 3.1 字体与字号

```css
/* 令牌真身见 3.11，此处是语义摘要 */
--fx-font-sans: system-ui, -apple-system, BlinkMacSystemFont, "SF Pro Text",
                "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
--fx-font-mono: ui-monospace, "SF Mono", Menlo, "PingFang SC", monospace;
```

| 用途 | 字号 | 字重 |
|---|---|---|
| 窗口/面板标题 | 13px | `font-emphasis`（500） |
| 正文与控件 | **13px** | 400 |
| 辅助文字 | 11px | 400 |
| 分组标题 | 11px | `font-emphasis`（500） |
| 通用指标数字 | 22px | 700 |
| 摘要页主标题 / 大数字 | 28px | 700 |
| 摘要页分区标题 | 15px | `font-emphasis`（500） |
| 摘要页辅助文字 | 12px / 18px 行高 | 400 |

摘要页（目前是工作台）使用 `text-display` / `text-title` / `text-subhead` 三个扩展令牌。
它不是全站字号整体放大：任务表和弹框仍保持 macOS 紧凑控件尺度，只有需要「远读」的统计，
才用更大的数字、更宽的行距和留白。

**字体栈是验证过的，别随手改。** 用 Chrome DevTools Protocol 的
`CSS.getPlatformFontsForNode`（唯一能回答「这个节点最终由哪套平台字体渲染」的接口）实测本机：

```
body 实测 → 西文/数字: .SF NS (.SFNS-Regular)      中文: PingFang SC (PingFangSC-Regular)
```

和 macOS 原生 App 的字体组合一致，所以**不存在「字体不对」的家族问题**。有两条纪律：

1. **`system-ui` 必须排第一位。** 它是唯一在 macOS Chrome 上稳定命中 SF 的条目；`-apple-system`
   与 `"SF Pro Text"` 在部分环境下解析失败，会一路穿透到 `sans-serif`，而 `sans-serif` 落下来的
   字体可能用中文字形去渲染西文——那才真的「字体不对」。
2. **不要塞 `"Helvetica Neue"`。** 它没有中文字形，排在中文项前后都无法兜底，只会让「西文优先」
   的注释变成谎话；而且它本身比 SF 更不像 Apple，只在 SF 缺失时才轮得到，纯属添乱。

> **切莫用 `document.fonts.check()` 判断字体。** 它对「本机装没装」和「CSS 有没有在用」是两码事，
> 对本机已安装字体一律返回 `true`，回答不了实际问题。基准页顶部的「字体探针」区块用的是
> canvas 像素指纹 + 中文步进比对，判读方法就写在那一块里。

> **中文强调用 500，不用 600。** `font-semibold` 在中文上落到 `PingFangSC-Semibold`，视觉重量
> 明显超过 SF 的 600，13px 下会把标题和表头压得发闷。全站强调统一走 `font-emphasis` 一个类
> （令牌 `--font-weight-emphasis: 500`），要调整只改这一处。基准页「字重与数字」区块可当场对比
> 400 / 500 / 600。
>
> **数据表格的数字必须等宽。** `TableColumn.numeric` 或右对齐的列会自动带 `.tnum`；
> 否则同一列各位数宽度不等，扫读时左边缘会跳。比例数字只用于正文。

> **第一条分水岭：控件字号 13px。**
> 通用 Web 应用用 14px 或 16px，macOS 原生控件的标准字号就是 13px。
> 只要把全站基准字号定成 13px，观感立刻就对了大半；定成 16px 则无论怎么调都像网页。
> 用 `-apple-system` 会直接拿到系统真实的 SF Pro，不要打包 webfont。
> （更正：实际起作用的是 `system-ui`，见本节上面的实测结论。）

### 3.2 圆角

| 元素 | 半径 |
|---|---|
| 控件（按钮 / 输入 / 下拉） | 6px |
| 卡片 / 弹出层 / 菜单 | 10px |
| 窗口 / 大面板 | 12px |
| 工作台卡片 | 16px |
| 徽章 / 小标签 | 4px |

> macOS 用的是连续圆角（squircle），CSS 的 `border-radius` 画不出真正的超椭圆。
> 上面这组值已经足够接近，**不要**为追求完美去套 SVG 遮罩，收益远小于复杂度。

### 3.3 控件尺寸

| 令牌（`size`） | 高度 | 用途 |
|---|---|---|
| `regular` | **24px** | 默认按钮、输入框、筛选栏 |
| `lg` | 28px | 窗口工具栏里的全部控件、主操作按钮 |
| `sm` | 20px | 表格内联操作、紧凑行内按钮 |

三个档位就是第五节组件 API 里 `size` 的取值，两处必须一致，不要另造 `large` / `small` 这类别名。

> **工具栏一屏只用一个档。** macOS 的窗口工具栏高 52px，里面的控件是 28px；混用 20px 与 24px
> 会让整条工具栏看起来「小一号」。所以工具栏内的分段控件、下拉、输入框、图标按钮统一 `lg`。

#### 3.3.1 图标尺寸与描边

图标走 `components/ui/icon/Icon.vue`，业务里**不要**直接 `<Settings :size="13" />` —— 尺寸散落在
各处的直接后果就是一排图标高低不齐。三档：

| `size` | 渲染 | 用在哪 |
|---|---|---|
| `inline` | 14px | 11px 辅助文字旁、按钮内联图标 |
| `regular` | 16px | 侧边栏行、24px 控件、正文行内 |
| `large` | 20px | 28px 工具栏按钮 |
| `xlarge` | 32px | 插图：空态、对话框。**不要出现在行内** |

**描边是反算出来的，不是固定值。** lucide 的 `viewBox` 是 24，渲染到 `px` 时描边等比缩放：
`stroke-width=1.5` 落到 16px 只剩 1px，比 13px 中文的竖画（约 1.3px）还细，于是图标发虚、
和旁边的字不在一个重量级。SF Symbols 在同字号下描边恒定，所以这里也照做：

```ts
// Icon.vue：任意尺寸下有效描边恒为 1.4px
const TARGET_STROKE = 1.4
const stroke = TARGET_STROKE * 24 / px
```

基准页的「图标尺寸」「图标描边」两个区块把这几种取值并排画出来，改之前先看一眼。

> 图标尺寸**不在 `tokens.css` 里再开一套 `--spacing-icon-*`**。描边要靠渲染尺寸反算，
> 这个数值必须在 JS 里拿到，令牌反而会变成第二个真相来源。尺寸表的唯一权威是 `Icon.vue`
> 里那张 `{ inline, regular, large, xlarge }` 映射。

> **第二条分水岭：控件高度比 Web 惯例明显矮。**
> 对照一下：Element Plus 默认 32px，antd 默认 32px，macOS 原生按钮约 22–24px。
> 这 8px 的差距是「像不像」的第二大决定因素。
>
> **为什么不取 AppKit 的 22pt：13px 中文（PingFang SC）在同尺寸框里的视觉密度高于同字号
> 西文，22px 高 + 13px 中文的实际观感是「挤」。** 所以基准定为 24px，三档差值统一 4px。
> 这条是实测结论，不是估算——基准页顶部有「尺寸实测」区块，直接把渲染高度打出来。

### 3.4 颜色

系统强调色（浅色 / 深色两套，深色用更亮的变体以保证对比度）：

```
blue    #007AFF / #0A84FF     ← 主强调色
red     #FF3B30 / #FF453A
orange  #FF9500 / #FF9F0A
yellow  #FFCC00 / #FFD60A
green   #34C759 / #30D158
gray    #8E8E93 / #98989D
```

语义标签色用**带透明度的黑/白**，而不是固定灰阶——这样叠在任何材质背景上都不会脏。
令牌名与完整取值以 3.11 为准，本节只讲取舍：

```
--label              rgba(0,0,0,0.85) / rgba(255,255,255,0.85)
--label-secondary    rgba(0,0,0,0.50) / rgba(255,255,255,0.55)
--label-tertiary     rgba(0,0,0,0.25) / rgba(255,255,255,0.25)
--separator          rgba(0,0,0,0.10) / rgba(255,255,255,0.13)
```

窗口与内容底色：

```
--window-bg    #ECECEC / #1E1E1E
--content-bg   #FFFFFF / #2C2C2E
```

输入类控件单独一组底色与描边，**不要直接复用 `content-bg`**——深色下输入框必须比内容底色
略亮，否则整片死黑、看不出可输入区域：

```
--field-bg       #FFFFFF / rgba(255,255,255,0.07)
--field-border   rgba(0,0,0,0.13) / rgba(255,255,255,0.13)
```

窗口按钮（traffic lights）**单独一组，不要拿 `--sys-red` 去画那个红点** ——
AppKit 画窗口按钮用的是另一套固定的窗口色，和控件语义色不是同一个值，混用会明显发暗：

```
--traffic-close      #FF5F57     红：能点，关掉弹框
--traffic-minimize   #FEBC2E     黄：模态窗口不能最小化，降透明度表示不可用
--traffic-zoom       #28C840     绿：同上，不能缩放
```

深浅两套取值相同：窗口按钮在深色下也不换色。为什么黄绿是「本色 + 降透明度」而不是纯灰，
见 14.1。

### 3.5 材质（Vibrancy）

侧边栏、工具栏、弹出菜单**必须用材质**，不能是纯色——这是 macOS 观感的灵魂所在。

```css
background: rgba(246, 246, 246, 0.72);
backdrop-filter: blur(30px) saturate(180%);
```

深色下底改为 `rgba(40, 40, 42, 0.72)`。

映射后的令牌是 `--material-bg`，深色下自动切换（见 3.11）；组件里只写 `bg-material backdrop-blur-[30px] backdrop-saturate-150`。

### 3.6 阴影与发丝描边

macOS 的阴影极轻，靠 **0.5px 发丝线**分层，而不是靠重阴影。

```css
--shadow-control: 0 0 0 0.5px rgba(0,0,0,0.05), 0 1px 2px rgba(0,0,0,0.08);
--shadow-popover: 0 0 0 0.5px rgba(0,0,0,0.08), 0 4px 16px rgba(0,0,0,0.14);
--shadow-window:  0 0 0 0.5px rgba(0,0,0,0.10), 0 12px 40px rgba(0,0,0,0.22);
```

> 0.5px 在 2x 屏上正好是一个物理像素，这正是 macOS 分界线的做法。
> 别用 1px 实线边框，那会立刻显出「网页感」。

四个层级各一档，**不要所有东西共用一档阴影**，否则层次全平：`shadow-control`（控件）、
`shadow-panel`（页面内的面板/卡片，常被误当成需要 `border` 的场景）、`shadow-popover`（弹层）、
`shadow-window`（窗口级）。

原始令牌为 `--fx-shadow-*`——加 `fx-` 前缀是为了避开 Tailwind 的 `--shadow-*` 命名空间，
否则 `@theme` 里会出现自引用。映射成工具类后才叫 `shadow-control` / `shadow-popover` / `shadow-window`，
组件里一律用工具类，不要直接写 `box-shadow`。

### 3.7 动效

```css
--ease-apple: cubic-bezier(0.32, 0.72, 0, 1);
```

| 场景 | 时长 |
|---|---|
| 悬停反馈 | 120ms |
| 弹出层展开 | 160ms |
| 窗口级过渡 | 200ms |

> **绝对不要做 Material 的涟漪（ripple）动画**，macOS 没有这个东西。
> 唯一的例外是新版按钮有极轻微的按压缩放 `scale(0.98)`，可以保留。

### 3.8 滚动条

**必须做覆盖式（overlay）滚动条**：默认透明，滚动或悬停时淡入，宽度 8px，胶囊形，`rgba(0,0,0,0.28)`。

macOS 用户对常驻的灰色滚动条极其敏感。这一条不做，前面所有细节都会被这一处拉低。

### 3.9 间距刻度

4px 网格，只用这几档，不要在组件里直写 `padding: 7px` 这类数字。

| 令牌 | 值 | 典型用途 |
|---|---|---|
| `1` / 4px | 4px | 图标与文字、徽章内边距 |
| `2` / 8px | 8px | 同排控件之间、列表项内边距 |
| `3` / 12px | 12px | 控件左右内边距、卡片紧凑内边距 |
| `4` / 16px | 16px | 卡片内边距 |
| `5` / 20px | 20px | 内容区左右内边距 |
| `6` / 24px | 24px | 区块之间 |
| `8` / 32px | 32px | 页面顶部留白 |

> ⚠️ **实现时最容易踩的坑：不要用 `html { font-size: 13px }` 来落实「全站 13px」。**
> Tailwind 的间距档位基于 `rem`（`p-4` = 1rem），把根字号改成 13px 会让整套间距刻度
> 静默缩水 19%，最终得到一个「哪都不对但找不到原因」的界面。正确做法是把 13px 设在
> `body` 上靠继承生效（见 3.11），`rem` 基准保持 16px 不动。

### 3.10 焦点环

键盘可达性必须有可见反馈，但 macOS 的焦点环只对键盘出现，鼠标点击不出环。

```css
--fx-focus-ring: 0 0 0 3px rgba(0, 122, 255, 0.35);   /* 深色下 rgba(10, 132, 255, 0.45) */
```

三条实现纪律：

1. 一律用 `:focus-visible`，**不要**用 `:focus`——否则鼠标点按钮也会留一圈蓝，macOS 上不会。
2. **全局兜底用 `outline`，组件内用 ring，不要用 `box-shadow` 画全局焦点环。**
   原因很实际：`shadow-control` 这类工具类本身就是 `box-shadow`，基线的规则优先级更低，
   会被组件自己的阴影直接覆盖，焦点环静默消失。`outline` 与 `box-shadow` 互不干扰，
   做兜底最稳；组件内部则用 ring（Tailwind 的 ring 是并入 `box-shadow` 的独立分量，不会互相踩）。
3. 输入框聚焦的表现是**边框变强调色 + 微弱外发光**，与按钮的 ring 区分开。

```css
/* 基线兜底：原生元素、链接、未封装控件 */
:focus:not(:focus-visible) {
  outline: none;
}
:focus-visible {
  outline: 2px solid var(--sys-blue);
  outline-offset: 2px;
}
```

```css
/* 输入框另走一套：改边框色 + 微弱外发光，与按钮的 ring 区分 */
.input:focus-visible {
  border-color: var(--sys-blue);
  box-shadow: 0 0 0 3px rgba(0, 122, 255, 0.15);
}
```

### 3.11 完整令牌文件（`src/styles/tokens.css`）

前面几节是说明，这一节是可直接落地的实现。四层结构：`@custom-variant` 定义暗色变体 →
`:root` / `.dark` 声明两套语义变量 → `@theme inline` 映射成 Tailwind 工具类 →
`@layer base` 放全站基线（这一层拆到 `global.css`）。

```css
/*
 * Regent 设计令牌 —— 对标的 macOS 原生观感全部收敛在这个文件里。
 * 约定见 FRONTEND.md 第三节：业务组件只引用令牌与工具类，不写裸颜色、裸尺寸。
 *
 * 四层结构：
 *   一层 @custom-variant  定义 .dark 暗色变体
 *   二层 :root / .dark    两套语义变量（raw，fx- 前缀避开 Tailwind 命名空间）
 *   三层 @theme inline    映射成工具类，inline 让工具类内联 var() 从而跟随主题切换
 *   四层 @layer base      全站基线，放在 global.css
 */
@import "tailwindcss";
@import "./global.css";

/* ── 一层：暗色变体 ─────────────────────────────────────────────────────────
   刻意不用 media query 直接驱动：media query 无法被「手动覆盖」，而我们既要跟随系统
   又要可手动切换。系统偏好由 index.html 的内联脚本翻译成 .dark 类。 */
@custom-variant dark (&:where(.dark, .dark *));

/* ── 二层：语义变量，浅色/深色两套，缺一不可 ─────────────────────────────── */
:root {
  color-scheme: light;

  /* 字体与缓动在深浅两套下取值相同，但也走 raw 变量：@layer base 要引用它们。
     这一栈用 Chrome DevTools Protocol 的 CSS.getPlatformFontsForNode 实测过本机解析结果：
       西文/数字 → .SF NS (.SFNS-Regular)      中文 → PingFang SC (PingFangSC-Regular)
     与 macOS 原生 App 的组合完全一致，所以不要随手改。两条纪律：
       1. system-ui 必须排在前面。若挪到末尾，Windows 会先命中 "Microsoft YaHei"，
          连西文也一起用雅黑渲染，西文就毁了。
       2. 不塞 "Helvetica Neue"。它没有中文字形，「西文优先」的说法站不住，
          而且它本身比 SF 更不像 Apple —— 只在 SF 缺失时才轮得到它，纯属添乱。 */
  --fx-font-sans: system-ui, -apple-system, BlinkMacSystemFont, "SF Pro Text",
    "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
  --fx-font-mono: ui-monospace, "SF Mono", Menlo, "PingFang SC", monospace;
  --fx-ease: cubic-bezier(0.32, 0.72, 0, 1);

  --sys-blue: #007aff;
  --sys-red: #ff3b30;
  --sys-orange: #ff9500;
  --sys-yellow: #ffcc00;
  --sys-green: #34c759;
  --sys-gray: #8e8e93;

  --label: rgba(0, 0, 0, 0.85);
  --label-secondary: rgba(0, 0, 0, 0.5);
  --label-tertiary: rgba(0, 0, 0, 0.25);
  --separator: rgba(0, 0, 0, 0.1);
  --separator-soft: rgba(0, 0, 0, 0.05);

  --window-bg: #ececec;
  --content-bg: #ffffff;
  --fx-dashboard-bg: #f3f7fd;
  --sidebar-bg: #f5f5f5;
  --material-bg: rgba(246, 246, 246, 0.72);
  --hover-bg: rgba(0, 0, 0, 0.03);
  --selection-bg: rgba(0, 122, 255, 0.12);

  /* 输入类控件的底色与描边单独一组：深色下输入框要比内容底色略亮，否则一片死黑 */
  --fx-field-bg: #ffffff;
  --fx-field-border: rgba(0, 0, 0, 0.13);

  /* 窗口红黄绿（traffic lights）。单列一组、不并进上面那套 --sys-*：
     AppKit 画这三颗用的是另一组固定的窗口色，和控件语义色不是同一个值 ——
     拿 --sys-red 去画会明显发暗。深浅两套取值相同：窗口按钮在深色下也不换色。 */
  --fx-traffic-close: #ff5f57;
  --fx-traffic-minimize: #febc2e;
  --fx-traffic-zoom: #28c840;

  --fx-shadow-control: 0 0 0 0.5px rgba(0, 0, 0, 0.07), 0 0.5px 1.5px rgba(0, 0, 0, 0.1);
  --fx-shadow-panel: 0 0 0 0.5px rgba(0, 0, 0, 0.05), 0 1px 3px rgba(0, 0, 0, 0.05);
  --fx-shadow-popover: 0 0 0 0.5px rgba(0, 0, 0, 0.07), 0 6px 20px rgba(0, 0, 0, 0.13);
  --fx-shadow-window: 0 0 0 0.5px rgba(0, 0, 0, 0.1), 0 12px 40px rgba(0, 0, 0, 0.22);
  --fx-focus-ring: 0 0 0 3px rgba(0, 122, 255, 0.35);

  --fx-scrollbar-thumb: rgba(0, 0, 0, 0.28);
}

.dark {
  color-scheme: dark;

  --sys-blue: #0a84ff;
  --sys-red: #ff453a;
  --sys-orange: #ff9f0a;
  --sys-yellow: #ffd60a;
  --sys-green: #30d158;
  --sys-gray: #98989d;

  --label: rgba(255, 255, 255, 0.85);
  --label-secondary: rgba(255, 255, 255, 0.55);
  --label-tertiary: rgba(255, 255, 255, 0.25);
  --separator: rgba(255, 255, 255, 0.13);
  --separator-soft: rgba(255, 255, 255, 0.08);

  --window-bg: #1e1e1e;
  --content-bg: #2c2c2e;
  --fx-dashboard-bg: #181b21;
  --sidebar-bg: #232325;
  --material-bg: rgba(40, 40, 42, 0.72);
  --hover-bg: rgba(255, 255, 255, 0.06);
  --selection-bg: rgba(10, 132, 255, 0.24);

  --fx-field-bg: rgba(255, 255, 255, 0.07);
  --fx-field-border: rgba(255, 255, 255, 0.13);

  /* 深色下投影基本不可见，分层靠发丝线，所以只留 0.5px 描边 */
  --fx-shadow-control: 0 0 0 0.5px rgba(255, 255, 255, 0.09);
  --fx-shadow-panel: 0 0 0 0.5px rgba(255, 255, 255, 0.06);
  --fx-shadow-popover: 0 0 0 0.5px rgba(255, 255, 255, 0.1), 0 8px 24px rgba(0, 0, 0, 0.5);
  --fx-shadow-window: 0 0 0 0.5px rgba(255, 255, 255, 0.12), 0 16px 48px rgba(0, 0, 0, 0.6);
  --fx-focus-ring: 0 0 0 3px rgba(10, 132, 255, 0.45);

  --fx-scrollbar-thumb: rgba(255, 255, 255, 0.28);
}

/* ── 三层：映射成 Tailwind 令牌 ───────────────────────────────────────────── */
@theme inline {
  --font-sans: var(--fx-font-sans);
  --font-mono: var(--fx-font-mono);

  /* 字号：13px 基准 */
  --text-caption: 11px;
  --text-caption--line-height: 14px;
  --text-body: 13px;
  --text-body--line-height: 18px;
  /* 控件里的文字用更紧的行高，垂直居中的光学重心才正 */
  --text-control: 13px;
  --text-control--line-height: 16px;
  --text-metric: 22px;
  --text-metric--line-height: 28px;

  --color-accent: var(--sys-blue);
  --color-danger: var(--sys-red);
  --color-warning: var(--sys-orange);
  --color-success: var(--sys-green);
  --color-neutral: var(--sys-gray);
  --color-label: var(--label);
  --color-label-secondary: var(--label-secondary);
  --color-label-tertiary: var(--label-tertiary);
  --color-separator: var(--separator);
  --color-separator-soft: var(--separator-soft);
  --color-window: var(--window-bg);
  --color-content: var(--content-bg);
  --color-dashboard: var(--fx-dashboard-bg);
  --color-sidebar: var(--sidebar-bg);
  --color-material: var(--material-bg);
  --color-hover: var(--hover-bg);
  --color-selection: var(--selection-bg);
  --color-field: var(--fx-field-bg);
  --color-field-border: var(--fx-field-border);
  --color-traffic-close: var(--fx-traffic-close);
  --color-traffic-minimize: var(--fx-traffic-minimize);
  --color-traffic-zoom: var(--fx-traffic-zoom);

  /* 强调字重。不要用 Tailwind 的 font-semibold（600）：中文的 600 落到 PingFangSC-Semibold，
     比 SF 的 600 视觉上重得多，13px 下会把标题/表头压得发闷。Apple 的中文 UI 强调用 Medium。
     全站强调统一走 font-emphasis 一个类，将来要调只改这一处。 */
  --font-weight-emphasis: 500;

  --radius-badge: 4px;
  --radius-control: 6px;
  --radius-panel: 10px;
  --radius-window: 12px;
  --radius-card: 16px;

  --spacing-toolbar: 52px;
  --spacing-toolbar-compact: 38px;
  --spacing-sidebar: 220px;
  /* 控件高度：13px 中文（PingFang）比同字号西文视觉更满，22px 会显得挤。
     取 24px 做基准，三档差值保持 4px 一致，同一屏不混用。 */
  --spacing-control-sm: 20px;
  --spacing-control: 24px;
  --spacing-control-lg: 28px;
  --spacing-row: 28px;
  --shadow-control: var(--fx-shadow-control);
  --shadow-panel: var(--fx-shadow-panel);
  --shadow-popover: var(--fx-shadow-popover);
  --shadow-window: var(--fx-shadow-window);

  --ease-apple: var(--fx-ease);
}
```

> 一至三层放 `src/styles/tokens.css`，第四层（`@layer base`）放 `src/styles/global.css`，
> 由 `tokens.css` 导入。拆开只是为了改基线时不误碰令牌。

### 3.12 字号与颜色同在 `text-` 前缀下 —— 必须告诉 tailwind-merge

Tailwind v4 生成工具类时，**字号和文字颜色都落在 `text-` 前缀上**：
`--text-control` → `.text-control`，`--color-label` → `.text-label`。

`tailwind-merge`（`tailwind-variants` 默认启用）只按前缀猜分组，遇到我们自造的
`label` / `control` 这类值，它一律归到「文字颜色」组。于是：

```js
tv({ base: 'text-label text-control' })()                    // → 'text-control'
tv({ base: 'text-control', variants: { v: { a: 'text-label' } } })()  // → 'text-label'
```

**后写的把先写的删掉，不报错、不警告。** 实测踩到的后果：

- `Input` 的文字颜色其实一直没生效，是靠继承 `body` 的 `color` 蒙对的。放进任何色彩上下文
  不一致的容器（比如基准页 `深浅并排` 里的 `.dark`）立刻现形 —— 深底上显示黑字。
- `Button` 的 13px 字号同理被删掉，一直靠继承。放进 `text-caption` 里会跟着缩成 11px。
- `Badge` 的 `text-caption` 被删掉，侧边栏徽章一直按 13px 渲染，比设计值大一档。

修法：给 twMerge 补上字号分组，并**把配置收在一个共享 `tv` 实例里**：

```ts
// src/components/ui/tv.ts —— 所有 *.variants.ts 必须从这里 import
export const tv = createTV({
  twMergeConfig: {
    extend: { classGroups: { 'font-size': [{ text: ['caption', 'body', 'control', 'metric'] }] } },
  },
})
```

新增字号令牌时**必须同步这张表**，漏了就是上面那种静默失效。

`npm run typecheck` 里挂了 `scripts/check-ui-conventions.mjs` 兜底：任何地方绕过共享 `tv`
直接 `import { tv } from 'tailwind-variants'` 会直接失败。这条约定用肉眼看是看不出来的，
只能靠检查脚本。

---

## 四、深浅模式的落地机制

观感分水岭之三：**深色模式不是「把颜色反过来」**，而是另一套独立的材质与对比度取值
（已在 3.11 的 `.dark` 中逐条给出）。

驱动方式用 **class**，不用 media query 直接驱动，因为要同时满足「跟随系统」和「手动覆盖」：

```html
<!-- index.html 的 <head> 内，必须在样式生效前同步执行，否则深色下会先闪一帧白 -->
<script>
  (function () {
    var saved = localStorage.getItem('regent-theme'); // 'light' | 'dark' | 'system'
    var dark = saved === 'dark' ||
      (saved !== 'light' && window.matchMedia('(prefers-color-scheme: dark)').matches);
    document.documentElement.classList.toggle('dark', dark);
  })();
</script>
```

- 对外只暴露 `useTheme()`：`{ theme, resolved, setTheme }`，`theme` 是三态（含 `system`），
  `resolved` 是最终生效的 `'light' | 'dark'`。
- 展示层只允许读 `resolved` 来切图标，不允许在业务组件里直接读 class 或 media query。
- 切换主题的短暂过渡：切换瞬间给 `<html>` 加 `.theme-switching`，200ms 后移除。
  **不要把 transition 常驻在 `*` 上**——那会让所有交互都变慢，且拖累列表滚动。

---

## 五、组件变体系统

前面全是「长什么样」，这一节是「二十多个组件怎么保证长成一个样」。没有这一层，第三次改动
之后按钮就会出现 4 种圆角和 3 种高度。

全部基础组件用 `tailwind-variants` 统一声明变体，**每个组件一个 `xxx.variants.ts`**，
组件本体只负责行为与无障碍：

```ts
// components/ui/button/button.variants.ts
import { tv } from 'tailwind-variants'

export const buttonVariants = tv({
  base: 'inline-flex items-center justify-center gap-1.5 rounded-control font-normal ' +
        'duration-[120ms] ease-apple transition-colors ' +
        'focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-accent/35 ' +
        'disabled:pointer-events-none disabled:opacity-40 active:scale-[0.98]',
  variants: {
    variant: {
      default: 'bg-content text-label shadow-control hover:bg-hover',
      primary: 'bg-accent text-white hover:brightness-105',
      danger: 'bg-danger text-white hover:brightness-105',
      ghost: 'bg-transparent text-label hover:bg-hover',
    },
    size: {
      sm: 'h-control-sm px-2 text-caption',
      regular: 'h-control px-3 text-body',
      lg: 'h-control-lg px-4 text-body',
    },
  },
  defaultVariants: { variant: 'default', size: 'regular' },
})
```

统一的 props 约定（所有基础组件一致，不例外）：

| prop | 取值 | 默认 |
|---|---|---|
| `size` | `sm` / `regular` / `lg` | `regular` |
| `variant` | `default` / `primary` / `danger` / `ghost` | `default` |
| `disabled` | `boolean` | `false` |
| `block` | `boolean`（占满整行） | `false` |

三条纪律：

1. **视觉变体只能加在 `tv()` 的 `variants` 里**，业务组件不得拼类名去改基础组件的内边距、圆角、字号。
2. 需要新外观时**加一个 variant**，而不是在调用处覆盖——覆盖一次就有第二次，令牌纪律从此失守。
3. 每个组件都导出 `xxxVariants`，业务组件想复用样式直接引用，不必再包一层壳。

`class` 透传只允许用于**布局**（外边距、宽度、栅格位置），这是唯一的例外。

---

## 六、组件清单

### 基础层（`components/ui/`，macOS 观感在这里封死）

**已落地（`regent-web/src/components/ui/`）**：
Button、IconButton、Input、Textarea、Select、Checkbox、RadioGroup、Switch、**SegmentedControl**、
Tooltip、Popover、DropdownMenu、Dialog（+ `DialogPanel` + `DialogField`）、Progress、Table、
**Sidebar**、Toolbar、
Badge、Avatar、EmptyState、Skeleton、Icon、**Toast**（`ToastHost` + `useToast`，见 15.4）、
**StatusDot**（列表里的「小色点 + 文字」）、**Pagination**（表格底部的读数与翻页，见 8.1）

**图表（`components/ui/chart/`）**：`DonutChart`（环形图，活动环同款圆头线帽 + 段间空隙）、
`TrendBars`（柱状图，只画柱子 + 一条基线，不画整条空轨道）。两个都是手写 SVG，
理由见第十七节。

**待做**：NumberInput、Combobox、Slider、Tabs、ContextMenu、Sheet

四个 macOS 特征最强、**必须手工做**的控件（通用库全都没有）：

| 组件 | 关键特征 |
|---|---|
| `SegmentedControl` | 灰色轨道 + 白色滑块，macOS 最标志性的控件 |
| `Switch` | 胶囊开关，滑块带细腻投影，切换有弹性动效 |
| `Sidebar` | 材质背景；选中项是强调色**圆角块**，不是整行高亮 |
| `Popover` / `DropdownMenu` | 10px 圆角 + 材质背景 + 菜单项左侧勾选标记 |
| `Dialog` | 左对齐文字 + 左上角图标 + 右下角按钮，见第十四节 |

`StatusDot` 是唯一一个**先重复了四次才抽出来**的组件：等级、任务状态、缺陷严重级、缺陷状态
四个徽章都是「小色点 + 文字」，抽之前改一次色点语义要改四处。色点由业务组件按枚举映射，
文字走插槽并继承颜色 —— 于是整行被选中（实心强调色 + 白字）时文字自动变白，
色点同时 `group-data-[selected=true]:bg-white` 转白。

> **蓝点落在蓝底上会整个消失。** 加 1px 白描边救不了（环太细，实测看不见），
> 唯一可行的是选中时把点整个转白 —— 原生列表也是这么做的。

### 业务层（`components/business/`）

**已落地**：`DecisionLevelBadge`、`TaskStatusBadge`、`BugSeverityBadge`、`BugStatusBadge`、
`DecisionReviewDialog`（批准与驳回共用，按 `mode` 切 alert / form 两种布局）、
`RunStatusBadge`（执行记录的五态色点，超时用灰不用红）、`RunDetailDialog`
（运行详情 + 人话视图 / 原始事件 / 出参原文三个页签，`layout="log"`，事实在前原文在后）、`NewTaskDialog`
（新建任务，`layout="form"` + `size="large"`；项目区三态 + 一个项目都没有时的直达注册 + 粘贴/拖放图片，
见 15.6）、`BugCreateDialog`（录入 Bug，`layout="form"` + `size="large"`；图片支持选择 / 拖放 /
⌘V / Ctrl+V，图片上传晚于 Bug 落库）、`ProjectFormDialog`（注册项目，`layout="form"`；工具栏 / 项目页 / 新建任务三处入口共用
同一个实例，所以它挂在 App 上而不是某个页面里）、`DirectoryPickerDialog`（选文件夹，
`layout="form"` + `size="large"`；照 NSOpenPanel 的排法，见 14.7）、`ImageAttachmentGrid`（附件缩略图墙，只画瓦片不画标题，任务与 Bug 的详情侧栏共用）、
`ImageAttachmentsDialog`（看图，`layout="log"`，
原图 + 下载；附件列表由调用方传入，它自己不拉接口）、`AppVersionDialog`
（全局 Footer 中的版本号入口，`layout="form"` + `size="large"`，桌面左侧版本记录、右侧详情，移动端下拉切换历史版本；
版本与发布说明集中放在 `content/releases.ts`）、`DeploymentDialog`（归属项目、服务器信息、认证方式、SSH 密码、部署脚本与固定参数契约，
`layout="form"` + `size="large"`）、`DeploymentRunConfirmDialog`（执行前只做二次确认）、
`DeploymentRunsDialog`（运行记录列表 + 轮询中的实时日志，`layout="log"`）、
`DeploymentRunStatusBadge`（部署状态的小色点）

**待做**：TaskRow、DecisionCard、ProjectSwitcher、DailyDigestPanel、
RequirementBoard、BugList

四个徽章承担四级 / 六态 / 四严重级 / 五态的视觉区分——**一律用系统色，不自造色**：
等级 L0 灰、L1 蓝、L2 橙、L3 红；状态 排队中/待批准橙、执行中蓝、完成绿、失败红、终止灰。
语义同一套（蓝=在跑、橙=在等人、绿=成了、灰=没有下文），所以跨页看不会串味。

---

## 七、布局骨架

三段式，对齐 macOS 应用的标准结构：

```
┌────────────────────────────────────────────────┐
│ Toolbar  52px（材质，项目切换 + 全局操作）        │
├──────────────┬─────────────────────────────────┤
│ Sidebar      │ Content（可滚动）                │
│ 220px        │                                 │
│ （材质）      │                                 │
├──────────────┴─────────────────────────────────┤
│ Footer  32px（版权 + 可点击版本号）               │
└────────────────────────────────────────────────┘
```

- Toolbar 高度 52px（紧凑模式 38px）。
- Sidebar 宽度 220px，折叠时**收到 0**，不要退化成 64px 的图标竖条——那是 Windows/Web 的习惯。
- 产品名「Regent AI 任务执行平台」固定常驻在**工具栏最左**。桌面端侧边栏开关位于侧边栏右边框的垂直中心，
  收起后停靠内容区左缘以便再次展开。品牌锁定使用 28px 方标（`R` + 蓝点）与双行文字组成，
  不再只铺两段普通文本。
  `Regent` 用 14px 强调字重，`AI` 用蓝色小标签承接产品属性，`任务执行平台` 用
  `text-label-secondary` 放在第二行；整体不抢中心页名。窄于 `lg` 时隐藏文字锁定、只保留方标，
  避免项目切换控件被挤压。展示层只写这一个产品名，代码名是 Regent —— 两者是同一个东西，不要各写各的。
- 浏览器标签标题 =「页面名 · Regent AI 任务执行平台」，随路由自动更新（`App.vue` 一处 `watchEffect`）。
- 侧边栏只保留业务入口，当前用户资料卡固定在底部。设置入口保留在工具栏；
  底部资料卡可打开个人资料，编辑显示名称并上传头像。设置页的「账户」段承接退出登录。
  入口按 macOS 侧边栏习惯分成「概览 / 执行与交付 / 平台配置」三组，用 10px 分组标题分隔，
  不做可折叠手风琴：分组是为了扫读，多一次展开只会拖慢每天都用的入口。
  任务工作区内用「任务 / Bug 单」页签切换，两类记录仍各自维护列表、检查器与领域状态。
  项目是唯一**不挂角标**的入口：角标是「等你处理」的意思，项目是配置不是待办 ——
  角标一旦不表示待办，就没人再看它了。
  基准页是给人查组件的参考页，不参与业务流程，也不当首屏 —— 它位于设置页的「样式基准」分区，
  `/baseline` 兼容旧链接并重定向到 `/settings/baseline`，`/` 直接重定向到 `/tasks`。
- 内容区背景用 `content`，与材质的侧边栏形成层次。
- 登录后的窗口底部固定一条 32px 全局 Footer（`AppFooter.vue`）：版权相对整个窗口居中，
  版本号贴最右侧；小屏把长版本号缩写为 `V1.0.2`，避免与版权重叠。点击版本号打开更新说明，
  完整版本号保留在按钮提示与更新弹窗中。版权与版本不再重复出现在分页条里。

---

## 八、表格与列表规格

任务列表和决策列表是这个平台出现频次最高的两个视图，也是最容易一眼看出「网页感」的地方。
通用组件库的表格默认值几乎全踩在 macOS 的雷区上（高行距、斑马纹、整行饱和蓝选中），
所以这一节单独定死：

| 部位 | 规格 |
|---|---|
| 表头 | 11px / 600 / `label-secondary`，高 24px，下边 0.5px `separator` |
| 行高 | 桌面 28px（`--spacing-row`）；触屏（`Table touchRows`，手机断点下）36px；字号 13px |
| 行分隔 | 仅行间 0.5px `separator-soft`；**不要外框、不要斑马纹** |
| 悬停 | `hover`（3% 黑 / 6% 白） |
| 选中 | **实心强调色 + 白字**，行内色点同时转白（`NSTableView` 的 `.regular` 高亮，Finder / 邮件 / 系统设置都这样）；窗口失焦时原生会退成灰色，这一档暂未做 |
| 数字列 | 右对齐 + `.tnum`（等宽数字），否则数字跳动会非常明显 |
| 行内操作 | 一行的**主要动作**可以常驻（决策页的「批准 / 驳回」就是这一行的全部意义，藏起来等于让人去猜）；**次要动作**一律收进悬停或 `...` 菜单 |
| 排序指示 | 8px 实心三角，不要用箭头图标 |
| 空态 | 用 `EmptyState` 组件，不要只留一行灰字。**一个屏幕只许有一个空态**：Table 自带「暂无数据」兜底，页面有自己的空态（「没有待批决策」这类）时必须传 `:show-empty="false"` —— 两个空叠在一起是最典型的半成品痕迹 |

任务表的列按「先认出这一行，再看细节」排：选择 / 任务 / 类型 / 项目 / 等级 / 状态 / 更新 / 耗时 / Token。
编号并进「任务」列，用弱化的 `#128` 前缀给出定位符，不再单占一列；标题因此拿到主要宽度。
等级在列表里只显示 `L0–L3`，完整含义放进 `title`；状态用 `StatusDot` 的小色点 + 文字；
类型只做轻量着色文字，不用胶囊。选定具体项目时项目列自动消失，因为这一列对每行都一样。

决策表沿用同一套层级：任务号、任务标题与待决问题合成「待决事项」一列，等级只留 `L0–L3`
并在规则抬级时加一个带说明的 `↑`，命中规则与提出时间到 `xl` 才展开。计划表把编号并进计划名，
执行时间缩成「月/日 时:分」，完整时间放 `title`；手机端只留计划、状态、任务数三列。

批量勾选与「当前详情行」是两套状态：勾选行用极淡强调色底，当前行仍是实心蓝。选中任意一行后，
表格上方才出现上下文操作条（已选数量 / 清除选择 / 批量派发 / 批量删除），不把长按钮常驻在工具栏里。

滚动与虚拟化：超过 200 行时上虚拟滚动（`@tanstack/vue-table` 只给逻辑，虚拟化需另接），
否则长列表的滚动会明显拖慢材质层的重绘。

### 8.1 分页

列表底部一条 32px（`h-footer`）的读数条。组件是 `components/ui/pagination/Pagination.vue`，
页码状态在 `composables/usePagination.ts`。

| 部位 | 做法与理由 |
|---|---|
| 位置 | 贴在表格容器**底部，不跟着表格滚**。表格负责滚，分页条负责说「你看到的是哪一段」 |
| 左边 | 只放读数 `1–20 / 共 49 条`，50% 标签色。试过 25%，白底上发虚 —— 这一条里唯一的文字就是它，而「一共多少条」是用户真要读的 |
| 右边 | 「每页 20 / 50 / 100 条」下拉 + `当前页 / 总页数` + 翻页 |
| 翻页控件 | **一个两段式步进控件**（NSSegmentedControl 的样子）：两段合在一个圆角容器里，中间一条发丝线。拆成两个独立圆角按钮，一是显得像两个功能，二是和工具栏里的图标按钮撞脸 |
| 默认每页 | 20 条。一屏放得下 20 行，所以「首屏要滚」和「第一页要翻」不会同时发生 |
| 空态 | 写「没有可显示的记录」，不写 `1–0 / 共 0 条` |
| 表头 | 必须吸顶，且**由调用方提供滚动容器** —— 理由见第十三节「表头不吸顶」那一行 |

分页条只负责读数与翻页，不再承载版权和版本号。二者统一放在窗口底部的全局 Footer，
这样无论当前页面有没有分页，产品信息和版本入口的位置都一致。

两条容易漏、漏了还不报错的边界，已经封在 `usePagination` 里：

1. **筛选条件变了要回第 1 页。** 停在原页码上会得到一张空表，而且看不出为什么空；
2. **总数变少时把越界的页码夹回去。** 驳回、关闭、缩小筛选都会让总数变小。

分页组件本身是受控的：`page` / `pageSize` 双向绑定，页码一变就触发一次请求。
列表改服务端分页之后，「当前页」不再由前端切片，而是 `?page=1&size=20` 下推给 SQL ——
组件、读数条、翻页控件一行都没动，这正是当初把它做成受控组件的回报。

---

## 九、明确不要做的事

1. **不要画假的红黄绿交通灯。** 「像 macOS 窗口」很诱人，但这三个点不可点、不能拖拽、关不掉，是纯粹的 UX 谎言。真想要窗口感，就做成真能用的（能折叠、能关闭）。
2. **不要用 Material 的涟漪、悬浮球、大扩散阴影。**
3. **不要用粗边框和饱和色块。** macOS 靠留白和发丝线分层，不靠色块。
4. **不要用 emoji 当图标。** 统一用 lucide，1.5px 线宽。
5. **不要把字号提到 14px 以上。** 这条是底线，破了前功尽弃。
6. **不要只做一半深色模式。** 两套令牌必须同时定义，跟随 `prefers-color-scheme`，并提供手动覆盖开关。只做浅色等于没做。
7. **不要在业务组件里写裸颜色和裸尺寸。** 一律引用令牌。这是防止观感随时间腐烂的唯一办法。
8. **不要给面板、分区、卡片套 1px 边框。** 这是最容易被写出来、也最毁观感的一条：一屏之内
   出现三四个描边盒子，立刻就变成线框图。层次靠 `bg-window` / `bg-content` 的底色差 + 一档
   `shadow-panel` + 极淡的 `separator` 发丝线，不靠边框。**边界只在「真正需要分隔」时出现**
   （侧边栏与内容之间、工具栏与内容之间、表格行之间），不要「每块都框一下」。

---

## 十、目录结构（`regent-web/`）

```
regent-web/
├── index.html
├── package.json
├── vite.config.ts
├── tsconfig.json
├── AGENTS.md                 # 前端约定：令牌纪律、组件规范
└── src/
    ├── main.ts
    ├── styles/
    │   ├── tokens.css        # ★ 设计令牌：暗色变体 + 两套语义变量 + @theme 映射
    │   └── global.css        # @layer base：滚动条、字体平滑、选中色、焦点环
    ├── components/
    │   ├── ui/               # 基础层，macOS 观感在此封死；每个组件配 xxx.variants.ts
    │   └── business/         # 业务层，只组装不改视觉
    ├── router/
    ├── views/                # 业务页；BaselineView 供设置页的样式基准分区复用
    ├── stores/               # Pinia：分页、筛选、搜索词、选中行都在这里
    │   ├── useTaskStore.ts
    │   ├── useDecisionStore.ts
    │   ├── useRunStore.ts
    │   └── useBugStore.ts
    ├── api/                  # 唯一的 HTTP 出口，按领域分文件
    │   ├── http.ts           # 拼 query、解 {code,message} 错误体
    │   ├── dashboard.ts      # 工作台快照：并行拉统计、趋势与最近事项
    │   ├── tasks.ts
    │   ├── decisions.ts
    │   ├── projects.ts       # 项目列表与注册
    │   ├── attachments.ts    # 贴图上传与下载（multipart / 二进制）
    │   └── runs.ts
    ├── composables/
    │   ├── useTheme.ts       # 三态主题（light / dark / system）
    │   ├── useToast.ts
    │   ├── usePagination.ts
    │   ├── useProjectDialog.ts      # 注册项目弹框的开关 + 最近一次注册成功的项目（见 15.6）
    │   ├── useOverlayLayer.ts       # 浮层层级：按打开顺序发号，不靠 DOM 顺序（见 14.6）
    │   └── useOverlayScrollbar.ts
    ├── utils/
    │   └── format.ts         # 耗时 / 相对时间 / token 数的展示格式
    └── types/
        ├── task.ts           # 领域词汇与 can* 守卫
        ├── bug.ts            # 缺陷单的词汇与状态机
        └── api.ts            # 后端 DTO 的镜像（含 DecisionSummary），见 15.6
```

---

## 十一、风险与备选

| 风险 | 说明 | 应对 |
|---|---|---|
| ~~`vue-tsc` 与 TS `7`~~ | 已实测：TS 7 不再导出 `./lib/tsc`，`vue-tsc` 直接崩溃 | 已回退 `typescript@6.0.3`，`vue-tsc` 正常；令牌与组件设计不受影响 |
| ~~Tailwind v4 命名空间~~ | 已实测：`--spacing-control` 正常生成 `.h-control{height:22px}`，`--text-*` / `--shadow-*` 同理 | 无需退回任意值写法 |
| 非 macOS 平台字体 | `-apple-system` 在 Windows / Linux 上退化为备用字体，观感明显不同 | 接受降级，不打包 webfont 冒充 SF，也不写第二套设计 |
| `backdrop-filter` 性能 | 材质效果在大面积列表滚动时可能掉帧 | 材质只用于侧边栏/工具栏/弹层，**不要**用于列表行 |
| 观感验收 | 文档描述得再细也无法替代肉眼确认 | 基准页已出，见第十三节 |

> 前两条已在脚手架阶段实测结清（见第十三节）。剩下的三条是长期约束，不是待办。

---

## 十二、下一步

1. ~~脚手架 `regent-web`~~ **已完成**，版本实测钉死
2. ~~落 `tokens.css` + `global.css`，做样式基准页~~ **已完成**
3. ~~你过一眼基准页~~ **已完成**（并因此暴露出表格选中、图标大小、中文强调字重三处问题）
4. ~~铺业务页面~~ **已铺任务 / 决策 / Bug 单 / 项目四条线**，见第十五节

> 2026-10-17 追加：侧边栏新增「测试用例」入口（`/test-cases`）。页面按项目、类型、启停与关键词筛选；
> 新建/编辑弹框支持选择 Web / 接口类型、执行命令与 UTF-8 测试文件上传。任务详情新增测试用例卡片，
> 可绑定最多 10 条用例；任务成功完成后自动执行。测试用例页还支持单条立即执行、勾选多条批量执行，
> 并在任务详情与用例页分别提供独立执行记录与输出查看。
5. ~~接后端接口~~ **已完成**，见 15.6。剩余基础组件：
   Tabs / Combobox / NumberInput / ContextMenu / Sheet；以及基准页里 `DialogPanel` 之外
   那三个没法静态平铺的弹层，改动后要手动点一遍

> 补充：阶段 1 后端可以先跑起来，前端不是硬阻塞。CLI 入口仍然是最小可用路径，
> 建议两者并行：后端打通执行链路的同时，前端先把样式基准页定下来。

---

## 十三、脚手架实测记录（2026-10-16）

`regent-web/` 已落地并验证：`vue-tsc --noEmit` 零错误、`vite build` 约 400ms、
深浅两套截图确认。以下几处与推导不同，记录在此以免下次重踩：

| 项 | 实测结果 | 处置 |
|---|---|---|
| TypeScript `7.0.2` | 不再导出 `./lib/tsc`，`vue-tsc@3.3.13` 启动即崩（`ERR_PACKAGE_PATH_NOT_EXPORTED`） | 钉 `typescript@6.0.3` |
| `lucide-vue-next@1.0.0` | npm 标记该包已废弃，提示改用 `@lucide/vue` | 换 `@lucide/vue@1.57.0`，import 路径同步改 |
| `--spacing-*` 自定义命名空间 | 正常生效：`--spacing-control: 22px` → `.h-control{height:22px}` | 无需退回任意值写法 |
| `@theme inline` | 令牌正常内联为 `var(--fx-*)`，因此 `.dark` 下换值即整站切换 | 保持；raw 变量统一 `--fx-` 前缀避免与 `--shadow-*` 命名空间自引用 |
| `dark:` 变体 | 编译为 `.dark\:xxx:where(.dark, .dark *)`，自建变体可用 | 只在必须显式分叉时用，常规一律靠令牌 |
| `tsconfig` 的 `baseUrl` | TS 6 起废弃，报 `TS5101` | 删掉 `baseUrl`，只留 `"paths": { "@/*": ["./src/*"] }` |
| 字体家族 | CDP 实测西文 `.SFNS-Regular`、中文 `PingFangSC-Regular`，与原生 App 一致；但 `-apple-system` / `"SF Pro Text"` / `"PingFang SC"` 这几个**字体名本身解析失败**，靠 `system-ui` 兜住 | 栈首必须是 `system-ui`；已删掉帮不上忙还误导的 `"Helvetica Neue"` |
| `text-rendering: optimizeLegibility` | 会在部分平台改变 kerning，13px 的字看起来比原生松 | 删掉，字距交还给字体 |
| `IconButton` 的宽度 | 死写 `w-control`，导致 `sm` 是 24×20、`lg` 是 24×28，一排图标按钮参差不齐 | 宽度跟 `size` 走，恢复正方形 |
| lucide 的描边 | 按 24px 网格设计，`stroke-width=1.5` 渲染到 16px 只剩 1px，比 13px 中文的竖画还细，图标发虚 | `Icon.vue` 反算成**有效描边恒为 1.4px**，不随尺寸变 |
| 工具栏控件尺寸 | 52px 高的工具栏里放 20px 控件 + 16px 图标，整体发小 | 统一 `size="lg"`（28px）+ `Icon size="large"`（20px），含分段控件与下拉 |
| 中文强调字重 | `font-semibold`(600) → `PingFangSC-Semibold`，比 SF 的 600 重得多 | 引入 `font-emphasis`(500)，全站强调统一走它 |
| 侧边栏行高 | 24px 行 + 16px 图标，图标只占 50% 黑，比文字发虚 | 行高提到 28px；图标改用 `text-label`；选中行的徽章换 `bg-white/25 text-white`（原先是灰底灰字，落在蓝底上几乎看不见） |
| 字号 vs 文字颜色 | 两者都在 `text-` 前缀下，tailwind-merge 当成同一组，后写的删先写的且不报错；Input 的颜色、Button 的字号、Badge 的 `text-caption` 全被删掉，一直靠继承蒙对 | 建 `src/components/ui/tv.ts` 给 twMerge 补 `font-size` 分组，四处变体统一改走它；加 `check-ui-conventions.mjs` 防回归 |
| `Select` 的 `class` | `<Select class="w-44">` 的 class 被**静默丢弃**：`SelectRoot` 是 renderless 的，槽里是「触发器 + Portal」两个节点，fragment 根拿不到自动 attrs 透传，而且没有告警 | `Select.vue` 改 `inheritAttrs: false` + 手动把 attrs 绑到触发器。给分页条的可定宽下拉才发现这件事，工具栏里的项目下拉之前一直在裸奔 |
| 表头不吸顶 | `Table.vue` 的根节点写了 `overflow-auto`，它于是成为最近的滚动盒子 —— 但它自己永远不滚，`position: sticky` 认的就是它，表头跟着内容一起滚走 | 根节点不再自带滚动容器，滚动交给调用方的 `scroll-area ... overflow-auto`。sticky 认对祖先后，滚 200px 表头仍在容器顶部 |
| 步进控件比下拉高 1px | 容器 `h-control`(24px) + 1px 边框 = 26px，旁边的下拉是 24px，并排看就是没对齐 | 容器写 `h-control`、两段写 `h-full`，含边框恒为 24px |
| `Textarea` 的 `rows` | 完全不生效：`size` 变体给的 `h-control` 和多行的 `h-auto` 同时挂在元素上，tailwind-merge 不认自定义 spacing 刻度，认不出这是一对冲突类，谁赢看产物顺序 | 把高度从 `size` 挪进 `compoundVariants`，只在非多行时输出，从根上不产生冲突 |

> 本机 npm 走的是自签证书代理，直连会报 `SELF_SIGNED_CERT_IN_CHAIN`（与源无关）。
> 装依赖时加 `--registry=https://registry.npmmirror.com --strict-ssl=false`，详见 `regent-web/README.md`。

---

## 十四、对话框与弹层

### 14.1 对话框：照着 NSAlert，不是照着 iOS

Web 上「居中、圆角、大标题、一蓝一灰双按钮」的那套是 iOS 警告框。macOS 的警告框不是这样，
三条必须记住：

1. **文字左对齐。** 标题与说明都靠左，只有图片类内容才居中。
2. **图标在左上角**，32px，不与标题同行。原生也不是每个警告框都带图标，普通确认可以不给；
   给了就是「这事有风险」的信号，所以破坏性操作才配。
3. **按钮在右下角，「取消在左、默认动作在右」。** 破坏性动作才允许用红色实心，
   且它**不该**落在回车默认位上。
4. **标题栏有红黄绿三颗窗口按钮**（`DialogPanel` 顶部，12px、间距 8px，AppKit 的实测值）——
   这是「独立窗口」而不是「网页浮层」最直接的信号。三颗里**只有红点能点**（关掉弹框），
   悬停整组才露出 ×，与 macOS 一致；黄绿是装饰，不进 tab 序列、不响应点击：模态窗口不能
   最小化也不能缩放，AppKit 在同样的窗口上就是把这两颗画成灰色。这里保留本色、只降透明度
   表示不可用 —— 浅色面板上的纯灰点会被当成没渲染出来。**摆了不能点的开关，比不摆更让人
   怀疑平台**，所以宁可拿透明度说实话，也不给它们编一个「点了会怎样」。

宽度只有三档（`form` 另有 `size` 两档），不要凭感觉加第四档：

| `layout` | 宽度 | 用来问什么 |
|---|---|---|
| `alert` | 340px | 「要不要做」——一行说明 + 两个按钮 |
| `form` | 384px | 「怎么做」——短表单，比如驳回时可补充理由 |
| `form` + `size="large"` | 640px | 「怎么把这件事说清楚」——长表单：整段指令、要贴图、还要选项目。384px 下这种表单每一行都在折行，人填起来是受罪 |
| `log` | 640px | 「到底跑了什么」——放日志与事件流。高度**不在 layout 里限**：让内容自己带 max-height 滚动，面板就不会出现「按钮被长日志顶出屏幕」这种要命的问题 |

`size` 只对 `form` 生效。`alert` 与 `log` 的宽度各自只有一档，被顺手传个 `large` 就变宽属于漂移，
所以 `dialog.variants.ts` 用 `compoundVariants` 把它们钉回去了 —— 这条规则在类型上救不了，只能靠这里说清楚。

### 14.2 为什么拆成 `Dialog` + `DialogPanel`

```text
Dialog.vue        模态壳：Portal、遮罩、焦点陷阱、Escape、居中定位、aria
  └ DialogPanel.vue   纯外观：标题栏窗口按钮、材质、圆角、阴影、内边距、标题/内容/按钮三槽
      └ dialog.variants.ts  唯一的样式来源
```

拆开的收益不是「分层好看」，而是**基准页能把同一块面板静态平铺出来**。弹层必须点开才出现，
意味着设计评审时谁都没法在一张图上比对三个变体 —— 于是只能靠嘴描述，歧义从这里开始。
平铺用的是同一个 `DialogPanel`，所见即真实弹出。

代价要讲清楚：`DialogPanel` 只负责外观，`aria-labelledby` 仍由 reka-ui 的
`DialogTitle` / `DialogDescription` 提供（写在 `Dialog.vue` 里）。所以**不要在业务里用
`DialogPanel` 造弹窗**，它没有焦点陷阱，是给样板和极端定制用的。

### 14.3 动效与遮罩

- 入场是「快速淡入 + 极轻微缩放」（遮罩 160ms、面板 200ms），不是 Web 常见的滑入。
- **`transform` 不能两边都写。** 居中定位用的是 `-translate-x-1/2 -translate-y-1/2`，
  动效若也动 `transform`，keyframes 的优先级高于类，两者会打架。所以定位留在
  `DialogContent`、`animate-panel-in` 留在 `DialogPanel`。
- **Popover / DropdownMenu / Tooltip 只淡入、不位移。** 它们的定位是 reka-ui 用内联
  `transform: translate3d(...)` 写死的，keyframes 里的 `transform` 会盖掉内联样式，
  动画期间整块面板会跳到屏幕左上角。
- 遮罩只压暗（`bg-black/25`），不模糊。macOS 是「父窗口退后」，不是「盖一层毛玻璃」。
- `prefers-reduced-motion: reduce` 时全站动画归零，见 `global.css`。

### 14.4 弹层组件一览

| 组件 | 用途 | 静态可平铺 |
|---|---|---|
| `Dialog` | 需要用户拍板的模态 | ✅ 走 `DialogPanel` |
| `Popover` | 锚在某个控件上的浮层 | ❌ 依赖 reka-ui 定位 |
| `DropdownMenu` | 命令菜单，含禁用项与分隔线 | ❌ 同上 |
| `Tooltip` | 悬停提示，4 秒内不重复 | ❌ 同上 |

后三个没法静态平铺是定位机制的必然结果，不是没做。基准页上它们只能点开看，
所以每次改这几处的材质，记得点一遍。

### 14.5 对话框里的字段排法：标签在左、右对齐

`DialogField`（`components/ui/dialog/`）是**对话框专用**的一行表单：左列 64px（`w-16`）
**右对齐**的标签，右列控件，`hint` 落在**控件下方**。

```text
  标识  ┌────────────────────────────┐
        │ app_audit                  │
        └────────────────────────────┘
        小写字母开头，字母数字下划线，建后不改
```

- 这是 macOS 对话框的字段惯例（Finder 的「新建文件夹」、钥匙串的「添加密码」、打印面板
  全是这个排法），正好和网页表单「标签在上、控件在下」相反。所以它是个对话框专用件：
  **页面里的表单仍然是标签在上，别拿去复用**；
- `hint` 跟着控件走，不跟着标签走 —— 64px 的标签列塞不下整句话；
- 标签用 13px 的主标签色，不是 11px 的次级色：原生对话框里字段名是正文级的信息，
  不是辅助说明。辅助说明才走 `hint`。

### 14.6 谁盖在谁上面：按「打开顺序」发号，不靠 DOM 顺序

**每个浮层都 teleport 到 body，插入点在组件挂载的那一刻就定死了**，之后开开关关都不会挪。
于是挂在 App 上的注册项目弹框永远排在路由页面里的弹框前面 —— 从新建任务里点「注册项目」，
它先渲染、再被后者的遮罩整块盖住（实测：面板上每一点 `elementFromPoint` 都命中那张遮罩）。
反方向的病根一模一样：弹框里的下拉框会被自己弹框的遮罩压住。

`composables/useOverlayLayer.ts` 因此给每个浮层按**打开顺序**发一个 z-index：50 起、
每层 +1、封顶 59；`ToastHost` 固定 60 —— 通知要是被浮层吃掉，人就永远看不到「保存失败」。
`Dialog` / `Select` / `Popover` / `DropdownMenu` 都从这里取号，**不要再写死 `z-50`**，
它们的根都是 renderless 的，拿不到开合状态就显式接管 `open`。

`Tooltip` 不在其中：它是 `pointer-events-none` 的纯装饰，而且弹框一开、body 上的
pointer-events 就被 reka-ui 关掉，鼠标根本落不到需要提示的按钮上 —— 它没有机会出现在弹框上面。

### 14.7 路径字段：能打，也能选

注册项目要填一个本机目录。让人手打一整条 `/Users/you/workspace/app-xxx` 是在给他制造错别字，
所以路径行是「输入框 + 选择…」：

```text
  路径  ┌────────────────────────────┐ ┌──────────────┐
        │ /Users/you/workspace/app   │ │  📂 选择…    │
        └────────────────────────────┘ └──────────────┘
        codex 的工作目录，必须在本机已存在
```

- **手打优先**：从别处复制一整条路径是最快的路径，输入框永远可以直接编辑；
- **选择… 打开 `DirectoryPickerDialog`**，照 NSOpenPanel 排：位置条在最上（「上一级」贴左，
  路径用等宽字体、超长时露出尾巴），下面一列子目录，单击选中、双击进入，方向键 + 回车也管用
  （挑目录是个来回翻的过程，不该逼人抓鼠标）；主按钮选的是**当前所在目录**，选中某一行时
  文案变成「选择「名字」」—— 和 macOS 一样，选了谁就选谁；
- **谁提供目录树**：后端（`GET /api/projects/directories`）。浏览器出于安全拿不到绝对路径，
  `<input type="file" webkitdirectory>` 只给目录名和相对路径，而注册要的正是绝对路径；
  后端与 codex 同机，它列出来的就是执行时真正的工作目录。只列子目录、不列 `.` 开头
  的隐藏目录（Finder 的默认）—— 选择器的结果要拿去当工作目录，`.git` 不是工作区；
- **失焦时校验一次**：路径不是空的且以 `/` 开头，就问一次后端「这个目录在不在」，
  读不到就把原因写在输入框下面、输入框转红边。判据只能在那一侧（也只有它问得到文件系统），
  前端只负责把结论摆在对应位置 —— 等提交才报错的话，人多半已经在想别的事了；
- 打开选择器时如果带着的路径读不到（手打的、刚被删的），**退回主目录并把原因写出来**：
  卡在报错上，人就只能关掉弹框想办法，那等于这个选择器没帮上忙。

---

## 十五、状态、数据流与提示

> 2026-10-16 落地。四页（任务 / 决策 / Bug 单 / 项目）都在真实接口上，前三条主线已经能跑通
> 「待批准 → 批准 / 驳回 → 续跑」的闭环，项目页是读侧（见 15.5），
> 这一节记录的是支撑它的纪律，不是愿望。

### 15.1 领域词汇照抄后端，不另起一套

前端 `src/types/` 里只放词汇，不放逻辑。`TaskStatus` 的七个值、`DecisionLevel` 的四个值、
`BugStatus` / `BugSeverity`，与 `ARCHITECTURE.md` 2.3 的状态机和 `TECH_STACK.md` 的表定义
**逐字一致**。

理由不是整洁：状态名两边一旦不一致，接真实接口时每个边界都要做翻译，而翻译层正是
「前端显示的和后端存的不是一回事」这类 bug 的温床。状态机图直接写在 `types/task.ts` 的注释里，
改状态必须同步改图、改 `can*` 守卫、改 `*StatusBadge` 的文案。

### 15.2 状态迁移只发生在 store 里

`src/stores/` 下两个 store：`useTaskStore`、`useBugStore`。视图**不写** `task.status = 'RUNNING'`，
一律调 store 的方法（`dispatch` / `approve` / `reject` / `retry` / `report` / `requestFix` …）。
方法开头先过守卫，守卫不过**直接抛** —— 对应 Java 侧的 `requireState(...)`。

这一层刻意照着后端聚合的样子写。接真实接口时，换掉的是每个方法体里的那几行赋值，
守卫和视图一行都不用动。

跨聚合的动作（缺陷 → 派发修复任务）**不在前端 store 里编排**：`useBugStore.requestFix`
只调用 `POST /api/bugs/{id}/fix-task`，由后端应用服务在一个事务里创建任务、派发并回填
`fix_task_id`。这是 `AGENTS.md` 硬约束 3 在前端的同一条纪律。

### 15.3 守卫是「按钮能不能点」的唯一依据

`types/task.ts` 与 `types/bug.ts` 各导出一组 `can*`。模板里写 `v-if="canResume(task.status)"`，
**不写** `v-if="task.status === 'BLOCKED'"`。前者改状态机不用回来改模板；后者漏改一处就是幽灵按钮
（点下去抛异常）。这条已经返工过一次，别再犯。

### 15.4 提示（Toast）

`composables/useToast.ts` + `ui/toast/ToastHost.vue`，宿主全站只在 `App.vue` 挂一次。

| 取舍 | 原因 |
|---|---|
| **不用 Pinia** | Toast 没有「状态」，只有一条事件流 + 一个队列。套 store 会让每个调用点多一行 `useStore()`，换不来任何东西。队列放模块作用域，import 到的是同一份 |
| 位置在**右上、工具栏之下**（`top-14 right-4`） | macOS 通知横幅的位置习惯。放正中最上方会和窗口标题打架，放右下会和右侧检查器抢视线 |
| **只淡入、不下滑** | 弹出层的定位是 reka-ui / fixed 给的，keyframes 里的 `transform` 会盖掉内联样式（见 14.3）。用 `TransitionGroup` 而不是每条自己写动画，连续触发多条时才不会后一条打断前一条的收起 |
| 只有**一个小色点**区分语义 | 复用 `StatusDot` 的颜色语言。不做彩色胶囊、不整块上底色 |
| 文案分两行 | 标题说**发生了什么**（`#1027 已批准`），说明说**接下来会怎样**（`执行器正从断点续跑`）。只有标题的提示等于没说 |
| `duration <= 0` = 常驻 | 留给「用户必须读到、不能自己消失」的提示 |

### 15.5 四个页面各自的取舍

| 页面 | 取舍 |
|---|---|
| 任务工作区 | 「任务 / Bug 单」两个页签。任务页左列表右检查器，排序是 `priority DESC, id DESC`；列表用来源徽标区分人工、需求与 Bug 修复。Bug 页沿用独立聚合和检查器，不能把缺陷拍平成任务 |
| 决策 | **默认只放需要人拍板的。** L0/L1 的自主决断只留痕、不进默认视图（切到「已处理」才看得到）—— 把它们也堆进来，真正待批的两条会被淹掉，人就开始无脑点批准，分级决策机制也就白做了 |
| Bug 单 | 按严重级排、同级新的在前。缺陷不像任务有先后关系，排序要回答的是「先修哪个」 |
| 项目 | **不分页**：项目是平台里数量最少的东西（个位数），分页是照猫画虎。任务数是每个项目一次 `size=1` 的查询换来的（加一个聚合接口现在不划算，数量涨到几十个再回头加）。**不摆假开关**：改名 / 暂停 / 删除都还没有接口（ARCHITECTURE.md 待办 19），一个点了没反应的暂停按钮，比没有这个按钮更让人怀疑平台 |
| 部署 | 每条部署归属一个项目并跟随顶栏项目过滤，服务器信息与脚本是配置，执行是快照。列表只显示项目、连接摘要和最近状态；密码在配置时加密保存，执行前只做二次确认；日志每 2 秒刷新且不记录密码 |

四页共用同一套骨架：工具栏（标题 + 数量 + 筛选控件 + 搜索）→ 表格 → 右侧 288px 检查器
（项目页同样：左侧表格 = 项目，右侧检查器 = 该项目的画像与最近任务）。
检查器里**标签在左、值在右**只用于短值（时间、Token）；任务标题这类长值改成上下两行，
288px 里塞不进一行两列 —— 这是 macOS 检查器的一贯做法。

**「已修复」和「已关闭」必须分开。** `FIXED` 只说明修复任务跑完了，`CLOSED` 才是人验过。
合成一个状态，等于让执行器自己宣布自己合格。

### 15.6 已经接上后端（2026-10-16）

三条主线的数据全部来自真实接口，`stores/` 里再没有种子数据。分工是这样的：

| 层 | 文件 | 只做一件事 |
|---|---|---|
| 契约 | `types/api.ts` | 后端 DTO 的镜像。字段名与后端一字不差，**不在这里补贴心的前端命名** |
| 请求 | `api/http.ts` | 唯一的 HTTP 出口：拼 query、统一解 `{code, message}` 错误体、抛 `ApiError`；写公网中继时带 CSRF 标记头 |
| 请求 | `api/tasks.ts` / `api/decisions.ts` / `api/runs.ts` | 按领域分组的端点函数，一个函数一个接口，不做缓存不做重试 |
| 状态 | `stores/useTaskStore.ts` / `useDecisionStore.ts` / `useRunStore.ts` | 记住用户选了什么（页码、筛选、搜索词），把结果放进来 |
| 展示 | `views/*.vue` | 只读 store，不自己 fetch |

几条落下来的规矩：

1. **领域词汇照抄后端。** `types/task.ts` 里只有 `TaskStatus` 这类枚举与 `canRetry` / `canCancel`
   这类守卫，没有一个「DTO 的本地版本」。多一层转换就多一处会漂移的地方。
2. **分页、筛选、搜索全部下推 SQL。** 前端不持有全量数据，`?page=&size=&status=&level=&keyword=`
   是唯一入口。搜索框加 250ms 防抖：后端分页之后每敲一个字就是一次请求。
3. **状态迁移不改本地字段，改完重新拉取。** 失败重试、终止任务重新执行和终止都是「调接口 → 重新 load」。
   本地改字段看起来更快，但后端一旦因守卫拒绝，界面就与库里分叉，刷新又变回去 —— 最难解释的一种 bug。
4. **后端连不上要说话。** 接口失败时 store 写 `error`，视图渲染成一条提示条而不是空白表格；
   提示里直接写「请确认后端已启动（默认 127.0.0.1:8787）」，因为这是本地开发最常见的原因。

**审批已经接上**（2026-10-16）：批准 / 驳回都会打 `POST /api/decisions/{id}/approve|reject`，
拿到的是**库里的最新状态**而不是「你点了什么」。决议完四处一起重拉（任务列表、状态统计、
右侧详情、决策卡）—— 少刷一处就会出现「列表说执行中、侧栏说待批准」这种自相矛盾的首屏。
失败时把后端的 message 原样透传：它是从聚合守卫里抛出来的，说的是「为什么这条现在批不了」
（已决议、任务不在阻塞中、令牌过期……），换成笼统的「操作失败」等于把唯一有用的信息丢掉。

令牌字段留成了可选：邮件链接带 token，控制台路径不带（本机/内网直连、尚无登录体系）。
这条口子写在 `DecisionApprovalService` 的注释里而不是藏在代码里 —— 哪天平台上了公网，
那里必须改成「一律要求令牌」。

**派发与运行记录也接上了**（2026-10-16）：`PENDING` 任务的检查器里有「派发给 codex」按钮，
打 `POST /api/tasks/{id}/dispatch`；取得执行位时进入 `RUNNING`，执行位已满则进入 `QUEUED`，
成功后任务列表、状态统计、右侧详情一起重拉（同审批的规矩）。
检查器下半部分是运行记录面板，按任务拉 `GET /api/tasks/{id}/runs`；只要有一条 `RUNNING`，
面板就每 3 秒静默刷新一次（`refreshQuietly` 不闪 loading，否则列表每 3 秒跳一下），
没有执行中的记录就停掉定时器 —— 常开的轮询会白烧后端连接。切任务时 `watch` 重拉、
组件卸载时清掉定时器。点开一条记录弹 `RunDetailDialog`（`layout="log"`）：

- **按钮文案要写出执行者是谁**（2026-10-16）：派发只支持把活交给本机 codex CLI（外部执行者
  那条路是它自己来认领，不经过这个按钮），所以按钮、批量按钮、tooltip、toast、空态提示
  一律明说「派发给 codex」而不说笼统的「执行器」—— 用户看到「已派发」但不知道派给了谁，
  就会以为这个平台能派给任意模型；

- **事实在前、原文在后**：状态 / 退出码 / 耗时 / token / 改动文件先摆出来，
  出参原文折叠在下 —— 人来看记录先问「成没成」，不会先问「模型原话是什么」；
- **谁在跑要一眼看得出来**（2026-10-16）：记录里多了 `executor`（`codex-cli` = 平台自己拉起 /
  `agent` = 外部执行者，见 `AGENT_PROTOCOL.md`）。外部执行者的行在列表里带一个机器人图标
  （悬停显示它的自称），弹框里多两行「执行者」「租约」。理由不是好看：外部执行者跑在平台
  看不见也杀不掉的进程里，它中途消失的样子就是一条停在原地的租约 ——
  界面上分不清这两者，用户会以为任务卡死了，而不是「干活的那个工具没了」；
- **三个页签对应三种读法**（2026-10-16）：默认「执行过程」是把事件流翻成人话的视图
  （翻译逻辑在 `components/business/runEvents.ts`，纯函数、与组件分开），「原始事件」逐条原样
  呈现、排错时以它为准，「出参原文」是平台读取结论的末条消息。起因是原始 JSONL 没人看得懂：
  类型是英文枚举、关键内容埋在两层 JSON 里，同一件事还被拆成 started / completed 两条；
- 人话视图只做提取与合并、不做解释性改写：同一 `item.id` 的开始与结束合并成一条（命令输出在
  跑完后补齐、待办清单就地更新勾选），执行器的话提成正文，命令与输出提成终端块（`exit_code`
  翻成「成功 / 退出码 N」），出参 JSON 提成「汇报结果」结论行，平台事件（认领、升级决策、
  租约回收）各自成条；认不准的事件落成「其他事件」原样展示。类型判定取 `run_event.event_type`
  列而非 payload 里的 `type` —— 平台自己产生的事件 payload 里根本没有 type 字段；
- 折叠的分寸：思考过程与长输出默认只露 3 行，展开按钮就在条目里；原始事件页签里能解析成
  JSON 的才缩进，解析不了就原样显示 —— 强行格式化会把一段普通文本拼成乱码。

**新建任务与项目都接上了**（2026-10-16）：

- 工具栏的 `+` 打开 `NewTaskDialog`（`layout="form"` + `size="large"`，640px）：项目 → 指令 → 图片 → 优先级。
  提交打 `POST /api/tasks`，返回的是新建任务的详情，所以建完能直接按 ID 找到并选中它 ——
  按标题去列表里找是不行的，列表带着分页与筛选，刚建的那条不在当前页是常态。
  建完把筛选条件全归位（状态、等级、项目、关键词、页码）——「新建成功却看不到」在用户眼里就是创建失败；
- **没有标题字段**：任务标题由后端从指令首行推导（剥掉 Markdown 记号、超过 60 字截断）。
  让人先写标题再写指令，等于让他把同一句话抄两遍，而抄出来的那份还会和正文越长越不像。
  界面上只在指令框下面留一句「第一行会被拿去当任务标题，不用另写」；
- **项目区三态**，规则是「上面选过就不再问一遍」：工具栏已选定某个项目 → 只显示一行只读的
  「项目：名称（code）」，不再要求选；工具栏是「全部项目」→ 必须选一个具体的（项目多于一个才给下拉，
  只有一个就直接用它 —— 让人在只有一个选项的下拉里点一下不产生任何信息）；一个项目都没有 →
  一行提示 + 一个「注册项目」直达按钮；
- **注册项目不常驻在新建任务里**。它原来在这里摊开过一张表单，现在开的是全站唯一那个
  `ProjectFormDialog`（挂在 App 上，三处入口共用，见下面「项目管理页」那条）：项目比任务长命得多，把注册挂在
  「新建任务 → 没有项目」这条一次性路径上，等于逼人「先想好一个任务 → 发现没有项目 → 顺手建一个」。
  只有「一个项目都没有」这个**死结**状态才在弹框里留一条活路 —— 否则人只能关掉弹框满世界找入口。
  注册成功后新项目立刻被选上，不用关掉弹框重来；
- **任务草稿**：项目、指令、优先级和已选插件存当前登录用户的一份服务端草稿。输入停顿 600ms 后保存，
  连续输入时最长 3 秒也会落一次；关闭弹框会补存，再次打开自动恢复。草稿与 Task 分离，不会进入待执行队列；
  恢复的项目若与工具栏筛选不同，项目区会重新给出选择器，避免草稿把当前项目锁死。图片不进入草稿，
  因为附件接口要求先有任务 ID；
- **图片附件**（有些任务光靠文字说不清：报错截图、设计稿、手画的原型）：
  - 三种入口：指令框里按 ⌘V 粘贴、把文件拖进整个弹框、点「添加图片」选文件。粘贴时若剪贴板里
    同时有文字则不动它 —— 从网页复制一段带图的富文本，人想要的多半还是那段文字；
  - 前端只做即时反馈（白名单 PNG / JPEG / GIF / WebP、单张 10MB、同名同大小去重），
    **真正的判据在后端**：浏览器给的类型标签来自扩展名，改个后缀就能骗过去；
  - 时序是**先建任务、再逐张上传**（`POST /api/tasks/{id}/attachments`）。附件接口挂在任务下，
    所以库里不可能出现「贴了图但任务没建出来」这种孤儿文件；代价是提交要多等一轮，
    这一轮用瓦片上的进度条表现出来（顺序传，不并发：一并发进度条就互相抢带宽，看起来像卡住了）；
  - 任一张失败就把弹框留在「任务已建好、图没传完」的状态：重试按钮就在眼前，
    关掉则提示可以去右侧详情里补。任务先落库这一点很关键 —— 图片传不上去不该把任务一起赔进去；
- 顶栏的项目下拉改为真正的筛选：选项来自 `GET /api/projects`，选中后列表按 `projectId` 重新查
  （`TaskQuery` 里是强类型 `ProjectId`，非法 ID 直接 400）。项目列表放在任务 store 里，
  所以弹框里刚注册的项目会立刻出现在下拉里，不用刷新页面；
- **图片在右侧详情里看得见、也补得上**：选中一行就拉一次附件列表（切任务重拉，放在视图里而不是
  store 里 —— 只有这一个地方用它），缩略图墙点开是 `ImageAttachmentsDialog`（原图 + 下载，
  多于一张时底部有一条缩略图条）。标题栏的「加一张图」走的是同一个上传接口 ——
  它填的正是「弹框里那张没传上去」这个坑，所以那句「之后在详情里补」不是空话。

- **Bug 单也能贴图**：录入弹框支持选择、拖放和 ⌘V / Ctrl+V；Bug 先落库，再逐张上传
  `POST /api/bugs/{id}/attachments`。上传失败时弹框保留重试，右侧 Bug 详情同样能看原图、下载和补图。

> 图片是**执行输入**而不是留档：每次尝试开局读一次（首跑与续跑都读），
> 逐张变成 `codex exec -i <path>`。所以往一个已经在跑的任务上补图，影响的是**下一次**执行 ——
> toast 里也是这么说的。

- **路径字段能打也能选**（`DirectoryPickerDialog`）：手打从别处复制来的整条路径，或者点
  「选择…」从后端列出的本机目录树里挑；失焦时后端校验一次，读不到就把原因写在输入框下面。
  排法与取舍见 14.7；
- **项目管理页**（`/projects`）是项目的读侧：左侧表格列画像（标识 / 路径 / 自动决策 / 启用状态 /
  任务数），右侧检查器给完整路径（等宽字体，`break-all`）、阈值说明与「该项目的任务」前 8 条，
  双击项目行或点检查器按钮可一步跳到任务页 —— 跳之前先把工具栏的项目筛选设成它，
  省得人到了任务页再选一次。
  **注册项目的三处入口**（工具栏项目下拉右边的 `+`、项目页右上角、以及上面那个死结状态）
  开的是同一个 `ProjectFormDialog`，它只挂在 App 上一次：挂在页面上就会出现互相看不见的三份开关。
  建完它把结果写进 `useProjectDialog().lastRegistered`，项目页靠这个「刚建了什么」的通知把新项目
  选中，新建任务弹框靠它把新项目填进项目区。用模块级 `ref` 而不是 pinia —— 这里只有一个布尔值
  和一个通知位，没有要拉的数据，建 store 只会多一层「为什么这件事在 store 里」的追问。

**还没接的**（不是遗漏，是接口还没有）：

- Bug 单的「标记已修复 / 验证关闭 / 不修复」状态流转 —— 人工录入、AI 发现、派发修复任务、
  列表筛选分页已经接真实接口；其余状态按钮不会在本地伪造状态；
- 需求录入与拆解 —— `POST /api/tasks` 录进来的是**任务**（来源 `MANUAL`），不是需求。
  入口叫什么就得建什么，否则人会以为系统已经会拆解需求了。

邮件里的「一键批准」入口（`POST /api/approvals/{token}`）也还没做：审批用例与令牌核销
已经就绪，缺的是邮件通道本身，等通知那一段落地时一起接。

## 十六、响应式与移动端

需求 82 明确要求「手机上访问，可以决策和发布任务」，所以窄屏不是「尽量能看」，而是
核心流程（看列表 → 打开一条 → 批准/驳回/派发）必须能走完。断点用 Tailwind 默认值：
`sm` 640 / `md` 768 / `lg` 1024，**统一以 `lg`（1024）作为「桌面 ↔ 手机」的分界** ——
它和侧边栏的折叠断点是同一个值，只有一套心智。

| 部位 | 桌面（≥lg） | 手机（<lg） |
|---|---|---|
| 侧边栏 | 占位两态，宽度动画 | 抽屉式覆盖（`fixed` + 位移），点遮罩或切页自动收起 |
| 两栏列表页（任务/Bug/项目） | 右侧 288px 检查器常驻 | 检查器变整屏覆盖层，顶部「‹ 返回列表」收回 |
| 表格 | 全列 | 次要列收起（`hideBelow`），只留能定位到那一行的列 |
| 页面筛选行 | 52px 单行 | 折行：标题/计数/筛选一行，搜索与操作占满一行 |
| 弹框 | 固定宽度（340/384/640） | `max-w-[calc(100vw-24px)]` 兜底，绝不横向溢出视口 |
| 分页 | 左读数 / 中版权 / 右控件 | 版权列在 `xl` 以下收起，读数与控件两栏；1024 宽度下页码不换行 |

两条规矩：

- **列收起不等于信息消失。**手机列表保留的是「能认出这是哪一行」的最小列（任务页 =
  勾选/编号+标题/状态；决策页 = 待决事项/批准驳回；计划页 = 计划/状态/任务数），完整信息在整屏详情与弹框里。
  收起规则写在列的 `hideBelow` 上（`table.types.ts`，支持 `sm | md | lg | xl | 2xl`），不散落在视图里。
  任务表的层级是：`xl` 以下收类型/项目/耗时/Token，`2xl` 以下收更新时间，`md` 以下收等级；
  状态列始终保留，手机端因此仍能一眼判断任务是执行中、待批准还是失败；
- **行高跟着输入方式走。**桌面维持 28px 的紧凑表格；手机端 `touchRows` 抬到 36px，
  复选框和状态文字都有足够的触控落点，但不会膨胀成 44px 的后台列表；
- **手机端不做自动选中。** 桌面自动选中第一项是为了「右栏不空着」；手机选中即整屏覆盖，
  落地页应该是列表本身 —— 所以项目页的自动选中带 `max-width: 1023px` 守卫。

验证方式：无头 Chrome `Emulation.setDeviceMetricsOverride` 以 390×844（iPhone 口径）
逐页截图，并测量 `main.scrollWidth === clientWidth`（不等即为横向溢出）。

---

## 十七、工作台与图表

工作台回答两个问题：**平台整体怎么样**、**我现在该动哪一件事**。视觉采用浅蓝画布、
白色卡片和蓝色强调色，参考企业数据看板的阅读节奏，而不是靠大面积渐变制造层次：

1. **运行焦点**：顶部是紧凑的白色横向带，只保留状态标签、动态结论、更新时间和主操作；标题不再使用首页 hero 级字号，
   避免工作台首屏被一块摘要卡占满；
2. **指标卡**：待处理、正在执行、任务总数、成功率、Token 消耗各自成为一张白卡，图标和关键数字负责着色；
   桌面卡高约 124px，手机端说明压到单行，让首个主工作区卡片能在首屏露出；
3. **主工作区**：桌面端按两列独立堆叠——左列是执行趋势、执行队列与任务构成，右列是待办中心与执行质量；
   两列各自按内容向下生长，不强行拉成同一行，避免较矮一侧出现大块空洞；
4. **卡片结构**：所有卡片延续白卡加发丝线，回答「系统由什么组成」「现在先处理什么」和「执行是否健康」。

### 17.1 指标卡：白底负责秩序，图标与数字负责提示

指标固定为五项（待处理 / 正在执行 / 任务总数 / 成功率 / Token）。五张卡片统一使用
`bg-content` 白底、`border-separator-soft` 发丝线与 `shadow-panel`，页面画布使用
`bg-dashboard` 的淡蓝底色。工作台的整体层次由「画布 → 白卡 → 发丝线」建立，不靠整卡渐变。

图标块承担“类别识别”，大数字承担“是否需要行动”：有待处理事项时用橙色，执行成功率
低于 90% 时才降级为橙/红，其余保持标签色。白色卡片让五类信息保持同一层级，报警数字
不会因为五张卡片同时有颜色而被稀释。

指标图标块是 32px 圆角方块（`rounded-panel`）+ 同色 12% 底色；
hover 才出现的箭头改为绝对定位，不占文字宽度，390px 下标签不会被无意义的空白挤断。
卡片圆角用 `rounded-card`（16px），不直接写 `16px`。

### 17.2 图表：手写 SVG，不引图表库

需求 90 要求「工作台重新设计」，最终只加了两个图：

| 图表 | 用在哪 | 关键做法 |
|---|---|---|
| `DonutChart` | 任务构成 | 圆头线帽、段间留 `gap + thickness`（线帽会向两端各多画半个线宽，不让位就会粘住）、单段占满整圈时不留缝 |
| `TrendArea` | 近 7 天收场 | 完成数用绿色面积线，失败数用低对比红色细线；非零点直接标数，今天加虚线基准与加粗标签 |

不引 ECharts / Chart.js 的理由：只需要两个图，而图表库会把一套自己的色板、字体、
动画和 tooltip 一起带进来 —— 这三样在这个项目里都已经有明确规范，接进来必然要压回去。
两个组件加起来不到 300 行，且颜色走**工具类**（`toneClass: 'text-success'`）或 CSS 主题变量，
深浅色主题、系统色令牌全部自动继承，图表里不会出现第二套色板。

趋势图不做悬浮 tooltip 作为唯一读数：关键点直接标数，触屏和键盘用户不 hover 也能读到。
SVG 只负责面积与折线；日期与数值标签用 DOM 定位，避免 `preserveAspectRatio="none"` 把文字压扁。
折线使用单调三次 Hermite 贝塞尔曲线：拐角圆润，但不会在尖峰或零点附近过冲而伪造数据。

`toneClass` 传类名而不是 `color` 属性，是因为 Tailwind 只认得源码里出现过的类名：
把 `text-success` 写在 TS 里（`statusDefinitions`）比在 JS 里拼 `#34C759` 更安全，
换主题时也不用改两处。

### 17.3 趋势数据的时区口径

`GET /api/tasks/trend?days=7` 返回**逐日补齐**的数组（没有收场的日子是 0，日期连续），
前端拿到直接画，不用自己补空档。切天在后端完成，口径是「调用方时区」：

- 时间列按约定存 UTC 字面量，SQL 里先 `DATE_ADD(finished_at, INTERVAL offsetMinutes MINUTE)`
  再取 `DATE`，范围条件同时按时区反推成 UTC 边界（不用 `DATE()` 直接比较，否则走不上索引）；
- 偏移量由应用层按「此刻」的时区算出来传给仓储。一个时区偏一小时，趋势图就会整体错位，
  而界面上看不出来 —— 所以这条口径写在仓储接口的注释里，不留在实现里。

### 17.4 响应式

- 指标带在手机上保持两列，Token 独占末行；
- 趋势、待办、任务构成与执行质量在 `xl` 以下按这个顺序纵向排列，待办紧随执行趋势，不会掉到页面最底部；
- 待办摘要固定三列；条目正文允许两行截断，指标说明允许两行，元信息始终单行省略，
  避免 390px 宽度出现横向滚动。

指标带的数字来自 `/api/tasks/stats`，任务构成来自同一份统计，近 7 天来自 `/api/tasks/trend`，
三者在 `api/dashboard.ts` 里并行发出：工作台不该为一屏数据串行等三次。
