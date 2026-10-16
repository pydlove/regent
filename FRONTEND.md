# Regent 前端 UI 定义

> 版本：v1.0
> 定义日期：2026-10-16
> 视觉目标：**对标 Apple macOS 原生应用观感**
> 配套文档：`TECH_STACK.md`、`ARCHITECTURE.md`、`AGENTS.md`

---

## 一、先说清一件事：没有现成的 macOS 组件库

我先把结论摆出来，避免走弯路：**npm 上不存在能直接上生产的 macOS 风格组件库。**

- Element Plus / TDesign / Naive UI / antd 的默认视觉是通用企业风或 Material，不是 macOS。
- 号称「macOS 复刻」的开源套件，要么早已停维护，要么只是静态截图拼的 demo，要么样式写死在组件内部改不动。

所以正确的路径是：**headless 原语组件（只管行为与无障碍）+ Tailwind + 自建 macOS 设计令牌**。

好处是观感完全可控，且不会因为组件库大版本升级而视觉回退——这对一个要长期运行、由 Codex 反复改动的内部平台很重要。
代价是要多写一层基础组件封装，约 15–20 个。这个成本是划算的，因为 macOS 观感恰恰藏在细节里，而细节只有自己写才控制得住。

---

## 二、技术栈

| 层次 | 选型 | 版本 | 说明 |
|---|---|---|---|
| 框架 | Vue | `3.5.44` | 按你此前明确的倾向选 Vue |
| 构建 | Vite | `8.3.4` | |
| 语言 | TypeScript | `7.0.2` | ⚠️ 见第八节风险 |
| 样式 | Tailwind CSS | `4.3.3` | 配合 `@tailwindcss/vite` `4.3.3` |
| 状态 | Pinia | `4.1.0` | |
| 无样式原语 | reka-ui | `2.11.0` | Radix 的 Vue 版，负责弹层/菜单/焦点管理/无障碍 |
| 表格 | @tanstack/vue-table | `9.3.1` | 只给逻辑，样式自己写，正好适合 macOS 表格 |
| 图标 | lucide-vue-next | `1.0.0` | 1.5px 线宽，最接近 SF Symbols 的中立选择 |
| 请求 | 原生 `fetch` 薄封装 | — | 不需要 axios |

**为什么不用 Element Plus**：它的视觉是通用企业风，要改成 macOS 得覆盖数百条 CSS 变量，改到最后是在跟它打架，而且它每次升级都会把观感拉回去。headless + Tailwind 反而是更短的路。

---

## 三、macOS 设计令牌（本文档的核心）

令牌集中在 `src/styles/tokens.css`，用 Tailwind v4 的 `@theme` 声明，业务组件一律引用令牌、不写裸值。

### 3.1 字体与字号

```css
--font-sans: -apple-system, BlinkMacSystemFont, "SF Pro Text",
             "Helvetica Neue", "PingFang SC", sans-serif;
--font-mono: ui-monospace, "SF Mono", Menlo, "PingFang SC", monospace;
```

| 用途 | 字号 | 字重 |
|---|---|---|
| 窗口/面板标题 | 13px | 600 |
| 正文与控件 | **13px** | 400 |
| 辅助文字 | 11px | 400 |
| 分组标题 | 11px | 600，字间距 `0.06em` |
| 仪表盘大数字 | 22px | 700 |

> **第一条分水岭：控件字号 13px。**
> 通用 Web 应用用 14px 或 16px，macOS 原生控件的标准字号就是 13px。
> 只要把全站基准字号定成 13px，观感立刻就对了大半；定成 16px 则无论怎么调都像网页。
> 用 `-apple-system` 会直接拿到系统真实的 SF Pro，不要打包 webfont。

### 3.2 圆角

| 元素 | 半径 |
|---|---|
| 控件（按钮 / 输入 / 下拉） | 6px |
| 卡片 / 弹出层 / 菜单 | 10px |
| 窗口 / 大面板 | 12px |
| 徽章 / 小标签 | 4px |

> macOS 用的是连续圆角（squircle），CSS 的 `border-radius` 画不出真正的超椭圆。
> 上面这组值已经足够接近，**不要**为追求完美去套 SVG 遮罩，收益远小于复杂度。

### 3.3 控件尺寸

| 尺寸 | 高度 | 用途 |
|---|---|---|
| regular | **22px** | 默认按钮、输入框 |
| large | 28px | 主操作按钮 |
| small | 18px | 表格内联操作 |

> **第二条分水岭：控件高度比 Web 惯例矮一半。**
> 对照一下：Element Plus 默认 32px，antd 默认 32px，macOS 原生按钮 22px。
> 这 10px 的差距是「像不像」的第二大决定因素。

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

语义标签色用**带透明度的黑/白**，而不是固定灰阶——这样叠在任何材质背景上都不会脏：

```
label            rgba(0,0,0,0.85) / rgba(255,255,255,0.85)
secondaryLabel   rgba(0,0,0,0.50) / rgba(255,255,255,0.55)
tertiaryLabel    rgba(0,0,0,0.25) / rgba(255,255,255,0.25)
separator        rgba(0,0,0,0.10) / rgba(255,255,255,0.13)
```

窗口与内容底色：

```
windowBackground   #ECECEC / #1E1E1E
contentBackground  #FFFFFF / #2C2C2E
```

### 3.5 材质（Vibrancy）

侧边栏、工具栏、弹出菜单**必须用材质**，不能是纯色——这是 macOS 观感的灵魂所在。

```css
background: rgba(246, 246, 246, 0.72);
backdrop-filter: blur(30px) saturate(180%);
```

深色下底改为 `rgba(40, 40, 42, 0.72)`。

### 3.6 阴影与发丝描边

macOS 的阴影极轻，靠 **0.5px 发丝线**分层，而不是靠重阴影。

```css
--shadow-control: 0 0 0 0.5px rgba(0,0,0,0.05), 0 1px 2px rgba(0,0,0,0.08);
--shadow-popover: 0 0 0 0.5px rgba(0,0,0,0.08), 0 4px 16px rgba(0,0,0,0.14);
--shadow-window:  0 0 0 0.5px rgba(0,0,0,0.10), 0 12px 40px rgba(0,0,0,0.22);
```

> 0.5px 在 2x 屏上正好是一个物理像素，这正是 macOS 分界线的做法。
> 别用 1px 实线边框，那会立刻显出「网页感」。

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

---

## 四、组件清单

### 基础层（`components/ui/`，macOS 观感在这里封死）

Button、IconButton、Input、NumberInput、Select、Combobox、Checkbox、Radio、Switch、Slider、
**SegmentedControl**、Tabs、Tooltip、Popover、DropdownMenu、ContextMenu、Dialog、Sheet、
Toast、Progress（条形 + 环形）、Table、**Sidebar**、Toolbar、Badge、Avatar、EmptyState、Skeleton

四个 macOS 特征最强、**必须手工做**的控件（通用库全都没有）：

| 组件 | 关键特征 |
|---|---|
| `SegmentedControl` | 灰色轨道 + 白色滑块，macOS 最标志性的控件 |
| `Switch` | 胶囊开关，滑块带细腻投影，切换有弹性动效 |
| `Sidebar` | 材质背景；选中项是强调色**圆角块**，不是整行高亮 |
| `Popover` / `DropdownMenu` | 10px 圆角 + 材质背景 + 菜单项左侧勾选标记 |

### 业务层（`components/business/`）

RequirementBoard、TaskList、TaskRow、DecisionCard、DecisionLevelBadge、ApprovalDialog、
BugList、RunTimeline、RunLogViewer、ProjectSwitcher、DailyDigestPanel

其中 `DecisionLevelBadge` 要承担 L0–L3 四级的视觉区分——用系统色而非自造色：
L0 灰、L1 蓝、L2 橙、L3 红。

---

## 五、布局骨架

三段式，对齐 macOS 应用的标准结构：

```
┌────────────────────────────────────────────────┐
│ Toolbar  52px（材质，项目切换 + 全局操作）        │
├──────────────┬─────────────────────────────────┤
│ Sidebar      │ Content（可滚动）                │
│ 220px        │                                 │
│ （材质）      │                                 │
└──────────────┴─────────────────────────────────┘
```

- Toolbar 高度 52px（紧凑模式 38px）。
- Sidebar 宽度 220px，折叠时**收到 0**，不要退化成 64px 的图标竖条——那是 Windows/Web 的习惯。
- 内容区背景用 `contentBackground`，与材质的侧边栏形成层次。

---

## 六、明确不要做的事

1. **不要画假的红黄绿交通灯。** 「像 macOS 窗口」很诱人，但这三个点不可点、不能拖拽、关不掉，是纯粹的 UX 谎言。真想要窗口感，就做成真能用的（能折叠、能关闭）。
2. **不要用 Material 的涟漪、悬浮球、大扩散阴影。**
3. **不要用粗边框和饱和色块。** macOS 靠留白和发丝线分层，不靠色块。
4. **不要用 emoji 当图标。** 统一用 lucide，1.5px 线宽。
5. **不要把字号提到 14px 以上。** 这条是底线，破了前功尽弃。
6. **不要只做一半深色模式。** 两套令牌必须同时定义，跟随 `prefers-color-scheme`，并提供手动覆盖开关。只做浅色等于没做。
7. **不要在业务组件里写裸颜色和裸尺寸。** 一律引用令牌。这是防止观感随时间腐烂的唯一办法。

---

## 七、目录结构（`regent-web/`）

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
    │   ├── tokens.css        # ★ 设计令牌，Tailwind @theme
    │   └── global.css        # 滚动条、字体平滑、选中色
    ├── components/
    │   ├── ui/               # 基础层，macOS 观感在此封死
    │   └── business/         # 业务层，只组装不改视觉
    ├── views/
    ├── stores/               # Pinia
    ├── api/
    ├── composables/
    └── types/
```

---

## 八、风险与备选

| 风险 | 说明 | 应对 |
|---|---|---|
| TypeScript `7.0.2` | TS 7 是原生（Go）重写版本，`vue-tsc` 的兼容性需实测 | 脚手架阶段实测；不可用则回退 TS `5.9.x` |
| Vite `8` + Tailwind `4.3.3` | 两者都是较新的大版本，插件兼容性需实测 | 不兼容则回退 Vite `7` |
| `backdrop-filter` 性能 | 材质效果在大面积列表滚动时可能掉帧 | 材质只用于侧边栏/工具栏/弹层，**不要**用于列表行 |
| 观感验收 | 文档描述得再细也无法替代肉眼确认 | 见第九节第 3 步，先出样式基准页 |

> 上面两个版本风险只在**脚手架阶段**（第九节第 1 步）才能定论，届时我会实际跑 `npm install` 并把冲突修掉，再把确定下来的版本回写到本文档。

---

## 九、下一步

1. 脚手架 `regent-web`：Vite + Vue 3 + TS + Tailwind v4 + Pinia，并实测钉死版本
2. 落 `tokens.css`，做一个**样式基准页**：把按钮 / 输入 / 开关 / 分段控件 / 表格 / 侧边栏 / 弹层全部排开，深浅两色并排
3. **你过一眼基准页** —— 这是唯一能真正验证「像不像 macOS」的方式，光看文档没有意义
4. 基准页确认后，`AGENTS.md` 里钉下令牌纪律，再开始铺业务页面

> 补充：阶段 1 后端可以先跑起来，前端不是硬阻塞。CLI 入口仍然是最小可用路径，
> 建议两者并行：后端打通执行链路的同时，前端先把样式基准页定下来。
