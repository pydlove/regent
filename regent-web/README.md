# regent-web

Regent 的前端工程。视觉目标是**对标 Apple macOS 原生应用观感**，
完整定义（令牌、组件、纪律）在仓库根目录的 `FRONTEND.md`，**改样式前必读**。

本工程**不参与 Maven 构建**，与后端 `regent-boot`（`127.0.0.1:8787`）通过 `/api` 代理通信。

## 快速开始

```bash
# 依赖安装：本机 npm 走自签证书代理，必须带这两个参数，否则报 SELF_SIGNED_CERT_IN_CHAIN
npm install --registry=https://registry.npmmirror.com --strict-ssl=false

npm run dev         # 开发服务器 http://127.0.0.1:12580
npm run typecheck   # vue-tsc，改完必须过
npm run build       # 产出 dist/
npm run preview     # 预览 dist/
```

## 目录

```
src/
├── styles/tokens.css     # ★ 设计令牌：暗色变体 + 两套语义变量 + @theme 映射
├── styles/global.css     # @layer base：滚动条、字体平滑、选中色、焦点兜底
├── components/ui/        # 基础层：macOS 观感在这里封死，每个组件配 xxx.variants.ts
├── components/business/  # 业务层：只组装，不改视觉
├── composables/          # useTheme（三态主题）、useOverlayScrollbar、useToast、usePagination
├── stores/               # useTaskStore / useBugStore：状态迁移的唯一发生地
├── types/                # 领域词汇（与 ARCHITECTURE.md 逐字一致）+ can* 守卫
├── views/                # BaselineView 供设置页的样式基准分区复用；其余为业务页面
└── router/
```

## 三条不能破的纪律（详见 `FRONTEND.md` 第五节）

1. 业务组件**不写裸颜色、裸尺寸**，一律引用令牌。
2. 视觉变体只能加在 `tv()` 的 `variants` 里，不在调用处覆盖基础组件的内边距/圆角/字号；
   `class` 透传只允许用于布局。
3. `useTheme()` 之外任何地方不得直接读 `class` 或 `prefers-color-scheme`。
4. `*.variants.ts` **必须** `import { tv } from '@/components/ui/tv'`，
   不许直接 `import { tv } from 'tailwind-variants'`。原因见 `FRONTEND.md` 3.12：
   字号和文字颜色都在 `text-` 前缀下，默认的 tailwind-merge 会把它们当冲突类互相吞掉，
   而且不报错。`npm run typecheck` 里有脚本拦这一条。

## 数据与状态

`stores/` 里现在装的是内存种子数据，刷新即还原。**但写法是照后端聚合来的**，别当 demo 随手改：

- 状态迁移只调 store 的方法（`dispatch` / `approve` / `reject` / `retry` / `report` / `requestFix`…），
  视图里**不许**直接赋值 `status`。方法开头先过守卫，不过就抛，对应 Java 侧的 `requireState(...)`。
- 按钮能不能点，一律问 `types/task.ts` / `types/bug.ts` 里的 `can*`：
  `v-if="canResume(task.status)"`，不要写 `v-if="task.status === 'BLOCKED'"`。
- 跨聚合的动作（Bug → 派发修复任务）由调用方编排两步，两个 store 各自只做自己那一半。
- 接后端时换的是 store 方法体里的那几行赋值，守卫与视图不动。

提示用 `composables/useToast` 的 `success / failure / warning / show`，
宿主 `ToastHost` 已经在 `App.vue` 挂好了，业务里不要再挂第二个。

列表用 `composables/usePagination` + `components/ui/pagination/Pagination.vue`：
传进去「已筛选好的全量列表」，出来的是「当前这一页」。**筛选条件一定要传 `resetOn`**，
否则改了筛选会停在第 3 页看一张空表。表头吸顶靠调用方提供滚动容器 —— `Table.vue` 刻意
不自带 `overflow-auto`，原因见 `FRONTEND.md` 第八节。

## 对话框

`dialog/` 下是两个文件，分工别搞混：

- `Dialog.vue` —— 模态壳：Portal、遮罩、焦点陷阱、Escape、居中定位、aria。**业务里用这个。**
- `DialogPanel.vue` —— 纯外观，没有焦点陷阱。它存在的唯一理由，是让基准页能把三个变体
  静态平铺出来好比对。**不要在业务里拿它当弹窗。**

`layout="alert"`（340px，问「要不要做」）/ `layout="form"`（384px，放表单）。
文字一律左对齐、图标在左上角、按钮在右下角且「取消在左」，这是 NSAlert 的样子不是 iOS 的。
详见 `FRONTEND.md` 第十四节。

## QA 钩子

`?theme=dark` / `?theme=light` 指定本次加载的主题，用于截图与深浅回归比对。
它**不写回** `localStorage`，所以不会污染正式偏好（正式行为看 `localStorage.regent-theme`，
三态：`light` / `dark` / `system`）。

## 两条硬约定

- **图标只用 `components/ui/icon/Icon.vue`**，不要在业务里直接 `<Settings :size="13" />`。
  四档 `inline`(14) / `regular`(16) / `large`(20) / `xlarge`(32，只给空态插图)，
  描边由脚本反算成「有效描边恒为 1.4px」。
  散写尺寸的后果是一排图标高低不齐，见 `FRONTEND.md` 3.3.1。
- **强调字重用 `font-emphasis`（500）**，不要用 `font-semibold`：中文的 600 落到
  `PingFangSC-Semibold`，比 SF 的 600 重得多，13px 下会把标题压闷。数字列记得 `numeric: true`
  或右对齐，走等宽数字。

字体栈与图标尺寸都是 CDP 实测后定下来的，改之前先看 `FRONTEND.md` 3.1 与 3.3.1；
设置页 `/settings/baseline` 上有「字体探针」「图标尺寸」「图标描边」「字重与数字」
四个区块可以当场比对；旧地址 `/baseline` 会重定向到该分区。

## 环境备注

- Node `v24.13.0` / npm `11.6.2`。
- TypeScript 钉在 `6.0.3`：TS 7 不再导出 `./lib/tsc`，`vue-tsc` 无法工作。
- 图标包是 `@lucide/vue`，旧的 `lucide-vue-next` 已废弃。
