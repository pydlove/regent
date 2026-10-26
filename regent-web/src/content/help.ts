export type HelpCategoryId =
  | 'start'
  | 'tasks'
  | 'testing'
  | 'decisions'
  | 'configuration'
  | 'records'
  | 'account'

export type HelpFilter = HelpCategoryId | 'all'

export interface HelpCategory {
  id: HelpCategoryId
  label: string
  description: string
}

export interface HelpLink {
  label: string
  to: string
}

export interface HelpAnswer {
  paragraphs: string[]
  steps?: string[]
  note?: string
  links?: HelpLink[]
}

export interface HelpTopic {
  id: string
  category: HelpCategoryId
  question: string
  keywords: string[]
  answer: HelpAnswer
}

export interface QuickStartStep {
  title: string
  description: string
  to: string
  action: string
}

export const HELP_CATEGORIES: readonly HelpCategory[] = [
  {
    id: 'start',
    label: '开始使用',
    description: '第一次进入平台时的主流程',
  },
  {
    id: 'tasks',
    label: '任务与执行',
    description: '录入、派发、排队与任务状态',
  },
  {
    id: 'testing',
    label: '测试与验收',
    description: 'Web / 接口测试用例、绑定与结果',
  },
  {
    id: 'decisions',
    label: '决策与审批',
    description: '分级规则、人工批准与驳回',
  },
  {
    id: 'configuration',
    label: '项目与配置',
    description: '项目、环境信息、插件与计划',
  },
  {
    id: 'records',
    label: '记录与排障',
    description: '运行记录、缺陷单与失败排查',
  },
  {
    id: 'account',
    label: '账户与安全',
    description: '登录、会话与敏感信息',
  },
]

export const QUICK_START_STEPS: readonly QuickStartStep[] = [
  {
    title: '新建项目',
    description: '研发项目只需组织与名称。创建后第一步配置项目团队角色，再创建版本和发起版本立项。',
    to: '/projects',
    action: '打开项目',
  },
  {
    title: '写清任务',
    description: '选择项目，写清改动范围、约束和验收标准。截图、设计稿可以直接粘贴或拖入。',
    to: '/tasks',
    action: '新建任务',
  },
  {
    title: '派发执行',
    description: '待执行任务可以手动派发。执行设备需要在线且 Codex 可用；任务排队时会等待执行位、设备或依赖条件满足。',
    to: '/tasks',
    action: '查看任务',
  },
  {
    title: '处理决策',
    description: '任务遇到超阈值决策会进入待批准。核对问题、方案、建议和影响范围后再批准或驳回。',
    to: '/decisions',
    action: '查看决策',
  },
  {
    title: '核对结果',
    description: '完成后查看运行记录、改动文件和验证结论。失败任务可重试，执行中发现的问题会进入缺陷单。',
    to: '/tasks',
    action: '查看记录',
  },
]

export const HELP_TOPICS: readonly HelpTopic[] = [
  {
    id: 'first-use',
    category: 'start',
    question: '第一次使用应该从哪里开始？',
    keywords: ['首次', '入门', '新用户', '流程'],
    answer: {
      paragraphs: [
        '先新建研发项目并配置团队角色，再创建版本、发起版本立项和录入需求。代码目录由开发成员后续绑定。',
        '任务开始执行后，通常只需要在工作台的待办或“任务 → 需求”处理真正需要人拍板的事项。',
      ],
      steps: [
        '新建研发项目并完成立项。',
        '创建任务，写清目标、限制和验收方式。',
        '派发任务，或在自动调度开启时等待平台领取。',
        '遇到待批决策时核对影响并批准或驳回。',
        '在运行记录中核对结果和改动。',
      ],
      links: [
        { label: '打开项目页', to: '/projects' },
        { label: '进入任务页', to: '/tasks' },
      ],
    },
  },
  {
    id: 'requirement-vs-task',
    category: 'start',
    question: '“需求”和“任务”有什么区别？',
    keywords: ['需求', '拆解', '来源', 'REQUIREMENT'],
    answer: {
      paragraphs: [
        '任务是可以独立执行、阻塞、重试和验收的工作单元。需求是更高一层的输入，一份需求未来可以拆成多个任务。',
        '现在可以在「任务 → 需求」录入业务目标，由 AI 检查是否需要澄清，再生成可编辑的任务草稿；确认后才会正式创建任务。',
      ],
      note: '直接创建的任务来源为“人工”，通过需求确认创建的任务来源为“需求”；两者都可以在执行控制塔中查看状态。',
    },
  },
  {
    id: 'install-codex',
    category: 'start',
    question: '新用户没有安装 Codex 怎么办？',
    keywords: ['Codex', '安装', '新用户', '未安装', '执行器', 'npm', 'brew'],
    answer: {
      paragraphs: [
        '没有安装 Codex 不影响注册、项目录入和浏览。平台只会在需要执行任务或生成项目摘要时提示执行器未就绪，不会把整个系统锁死。',
        '登录后平台会持续检测 Codex。顶部提示条可以直接打开安装弹框，选择 npm 或 Homebrew 一键安装，也可以复制命令到终端自行安装。',
        '安装后回到「设置 → 执行」重新检测即可。如果 Codex 不在系统 PATH 或常见安装目录里，可以在后端 application-local.yml 的 regent.execution.codex-command 中填写绝对路径。',
        '浏览器通过公网检测本机时，页面必须使用 HTTPS。首次检测请允许浏览器的“本地网络访问”；macOS 只使用浏览器时执行一次 deploy/local/device-agent.sh install，Windows 则运行便携包里的 Install-Device-Agent-Autostart.cmd，设备代理会常驻并在登录后自动启动。',
      ],
      steps: [
        '在本机执行 npm install -g @openai/codex，或使用 brew install codex。',
        '确认终端里可以直接运行 codex --version。',
        '打开「设置 → 执行」，点击「重新检测」。',
        '状态变为可用后，再派发任务或生成项目摘要。',
      ],
      note: 'npm 和 GitHub 位于国外，国内网络通常需要代理或“魔法”才能稳定安装。检测只验证 codex 可执行文件和版本命令，不验证 OpenAI 登录状态；首次使用还需要按 Codex 官方方式完成登录。',
      links: [{ label: '打开执行设置', to: '/settings/execution' }],
    },
  },
  {
    id: 'why-not-started',
    category: 'tasks',
    question: '任务创建后为什么一直显示“待执行”？',
    keywords: ['待执行', '不启动', 'PENDING', '自动派发', '调度'],
    answer: {
      paragraphs: [
        '新建任务不会默认立即开始。你可以打开任务详情，点击“派发给 codex”。',
        '如果部署开启了自动调度，平台也会领取符合条件且尚有执行位的任务。本地开发环境可能出于安全考虑关闭了自动派发。',
      ],
      links: [{ label: '前往任务页', to: '/tasks' }],
    },
  },
  {
    id: 'queue-recovery',
    category: 'tasks',
    question: '任务排队很久没有开始，刷新或服务重启会丢失吗？',
    keywords: ['排队', '等待设备', '执行位', '刷新', '重启', '恢复', 'QUEUED', '设备离线', '依赖'],
    answer: {
      paragraphs: [
        '排队任务会保存在平台数据库中，刷新页面不会丢失。后端重启后，设备下一次心跳会重新发现当前账号下仍可领取的排队任务；已终止、已完成或依赖未完成的任务不会被重新投递。',
        '任务长时间排队通常有三种原因：当前并发执行位已满、没有在线执行设备，或任务依赖的前置任务还没有完成。先看任务详情的阻塞原因和最后活动时间，不要连续重复派发。',
      ],
      steps: [
        '确认执行设备在线，设置页中的 Codex 状态可用。',
        '检查执行设置里的并发上限，以及当前正在运行的任务数量。',
        '检查任务依赖；依赖未完成时，任务应保持等待而不是执行。',
        '如果任务已被终止，设备代理会停止重试；需要继续时重新创建或重试任务。',
      ],
      note: '设备代理离线后重新启动，平台会通过心跳恢复可领取的排队任务，不会因为页面刷新自动生成重复任务。',
      links: [{ label: '打开执行设置', to: '/settings/execution' }],
    },
  },
  {
    id: 'cc-switch-download',
    category: 'start',
    question: '如何安装 CC Switch 来管理多个 Codex 配置？',
    keywords: ['CC Switch', 'cc-switch', '下载', '安装', 'Codex', '供应商', '配置切换'],
    answer: {
      paragraphs: [
        'CC Switch 是第三方开源桌面工具，可统一管理 Codex、Claude Code 等工具的供应商配置、MCP 与 Skills。',
        '打开「设置 → 执行」，页面会根据当前系统提供官方最新版的快捷下载，同时列出 Homebrew、Windows 安装版/便携版和 Linux 下载入口。',
      ],
      steps: [
        '进入「设置 → 执行」，找到 CC Switch 面板。',
        '点击快捷下载；macOS 也可以执行 brew install --cask cc-switch。',
        '安装后打开 CC Switch，按工具文档添加或切换供应商配置。',
      ],
      note: '下载地址来自 CC Switch 官方 GitHub Releases。若网络无法读取最新版本，可直接打开官网或 Releases 页面下载。',
      links: [{ label: '打开执行设置', to: '/settings/execution' }],
    },
  },
  {
    id: 'task-draft',
    category: 'tasks',
    question: '新建任务写了一半，内容会自动保存吗？',
    keywords: ['草稿', '自动保存', '恢复', '关闭', '清除'],
    answer: {
      paragraphs: [
        '会。项目、指令、优先级和已选插件会在输入停顿后自动保存；即使连续输入，也会按最长等待时间定期落一次草稿。',
        '关闭弹框后再次打开会恢复上次内容。点“清除草稿”会删除当前登录用户的草稿，不会影响已经创建的任务。',
      ],
      note: '待上传图片不会进入草稿。图片必须在任务创建后上传，因此关掉弹框时尚未提交的图片不会被保留。',
    },
  },
  {
    id: 'task-continuation',
    category: 'tasks',
    question: '如何基于已有任务继续创建后续任务？',
    keywords: ['继续创建', '后续任务', '接续', '同一会话', 'thread', 'Codex 会话'],
    answer: {
      paragraphs: [
        '打开一条已经建立 Codex 会话的任务，在详情底部的操作栏点击“继续创建”。新任务会复用来源任务的 thread_id 和分支，但拥有独立的标题、指令、状态与执行记录。',
        '这样可以要求 AI 接着已有上下文继续开发，而不是从零重抄原任务。为避免同一会话被并发写入，来源任务完成、失败或终止后才能派发后续任务。',
      ],
      steps: [
        '选择来源任务，确认详情底部出现“继续创建”。',
        '填写这次要补充或修改的开发内容；截图可以按 Ctrl+V / ⌘V 粘贴，也可以拖放或选择图片，并按需调整优先级。',
        '创建后等待来源任务收场，再派发新任务。',
      ],
      note: '后续任务只继承 Codex 会话与分支，不自动复制来源任务的附件和插件绑定；本次新粘贴的图片会作为新任务附件上传。',
      links: [{ label: '前往任务页', to: '/tasks' }],
    },
  },
  {
    id: 'task-branch',
    category: 'tasks',
    question: '任务会在哪个 Git 分支上执行？没有 Git 怎么办？',
    keywords: ['分支', 'Git', '当前分支', 'feature', '隔离', '切换分支', 'branch', '多仓库', '总目录'],
    answer: {
      paragraphs: [
        '如果项目根目录本身是有效 Git 仓库，任务默认直接在当前分支上执行；如果它是普通目录、空仓库，或包含多个 Git 仓库的父目录，任务直接在目录当前状态执行，分支字段为空。',
        '派发前不会预建任务分支，也不会因为创建分支单独生成审批。',
        '只有执行器在成功出参中主动请求隔离时，平台才会创建或切换到 feature/... 分支；未给出分支名时按任务号生成。运行记录会保留这次尝试实际使用的分支。',
      ],
      note: '多个任务共享同一个工作区时，要留意当前分支是否适合继续执行；需要完全隔离时，应在任务指令中明确要求使用独立分支。',
    },
  },
  {
    id: 'write-good-instruction',
    category: 'tasks',
    question: '怎样写一条更容易执行成功的任务指令？',
    keywords: ['指令', '提示词', '验收标准', '描述', '怎么写'],
    answer: {
      paragraphs: [
        '第一行会被提取为任务标题，因此先用一句话说明要完成什么，再展开细节。',
        '指令不需要写成固定模板，但至少要让人和 AI 都能判断“什么算做完”。',
      ],
      steps: [
        '说明要改哪一部分，以及明确不要改什么。',
        '写出必须满足的约束，例如兼容性、性能或设计规范。',
        '列出可执行的验收标准，最好包含要运行的检查命令。',
        '补充相关报错、复现步骤或截图；说不清时直接贴图。',
      ],
    },
  },
  {
    id: 'voice-input',
    category: 'tasks',
    question: '如何用语音输入任务指令？',
    keywords: ['语音', '麦克风', '翻译', '中文', 'English', 'HTTPS'],
    answer: {
      paragraphs: [
        '在新建任务的指令框上方点击“语音输入”，选择说话语言后开始录音。录音会每 2.5 秒切段并交给本机 Whisper 转写，文字会边录边追加到指令中；停止录音时再提交最后一段。',
        '普通话由本机 Whisper 直接转为简体中文；English、日本語和 한국어 会尝试使用浏览器翻译能力转成中文。若浏览器不支持翻译，会保留识别原文并给出提示。',
      ],
      note: '语音输入只在 HTTPS 或本机 localhost 下可用，推荐最新版 Chrome、Edge 或 Safari。录音只发送给本机 Regent 做离线转写，不写数据库；非中文翻译仍取决于浏览器 Translator 能力。',
      links: [{ label: '新建任务', to: '/tasks' }],
    },
  },
  {
    id: 'task-statuses',
    category: 'tasks',
    question: '任务状态分别代表什么？',
    keywords: ['状态', 'PENDING', 'QUEUED', 'RUNNING', 'BLOCKED', 'DONE', 'FAILED', 'CANCELLED'],
    answer: {
      paragraphs: [
        '待执行表示尚未派发；排队中表示已进入执行队列，正在等待执行位、设备或依赖条件；执行中表示执行器正在工作。',
        '执行中没有新事件时，不一定代表失败。请结合最后活动时间、设备状态和执行详情判断，不要只看状态标签。',
        '待批准表示任务被一条需要人工决定的决策阻塞；已完成表示本轮执行通过并收敛；失败表示执行没有成功，可人工重试；已终止表示任务被人工停止，运行记录和会话仍会保留。',
      ],
    },
  },
  {
    id: 'priority-and-queue',
    category: 'tasks',
    question: '优先级和并发上限如何影响排队？',
    keywords: ['优先级', '并发', '排队', '同时运行', '执行设置'],
    answer: {
      paragraphs: [
        '优先级数值越大，越优先被派发，默认值为 0。多个任务同时等待时，平台会先选择优先级更高的任务。',
        '平台同时运行的任务数受执行设置中的并发上限控制，可在设置页调整。调低上限不会打断正在运行的任务，只会让后续任务继续等待。排队任务会持久化保存，并在设备心跳或执行位释放后继续尝试。',
      ],
      links: [{ label: '打开执行设置', to: '/settings/execution' }],
    },
  },
  {
    id: 'edit-retry-cancel',
    category: 'tasks',
    question: '任务可以修改、终止或重试吗？',
    keywords: ['编辑', '终止', '取消', '重试', '重新执行', '删除'],
    answer: {
      paragraphs: [
        '已完成任务不能再次编辑。其他任务可以调整指令与优先级，但执行中的修改不会回头改变已经发送给当前会话的上下文。',
        '执行中的任务可以终止，历史运行记录和会话会保留。失败或已终止的任务可以重试；重试会建立新的推进过程，而不是抹掉上一次记录。',
      ],
    },
  },
  {
    id: 'attachments',
    category: 'tasks',
    question: '哪些图片可以作为任务附件？',
    keywords: ['图片', '附件', '截图', '粘贴', '拖放', '格式', '大小'],
    answer: {
      paragraphs: [
        '支持 PNG、JPEG、GIF 和 WebP，单张上限 10 MB。可以在指令框粘贴、拖入弹框，或通过“添加图片”选择文件。',
        '图片属于执行输入。任务开始后补上的图片，会在该任务的下一次执行尝试中生效。',
      ],
      note: '为安全起见不接受 SVG。截图比文字更容易说明界面问题和报错现场。',
    },
  },
  {
    id: 'task-documents',
    category: 'tasks',
    question: '任务文档和文件库怎么用？',
    keywords: ['文档', '文件库', 'PDF', 'Word', 'Excel', '复用', '上传'],
    answer: {
      paragraphs: [
        '新建任务时可以选择 PDF、Office、OpenDocument、Markdown、纯文本和结构化文本，单份上限 25 MB。执行时平台会把文档路径作为只读资料交给执行器。',
        '上传时可以逐份勾选“保存到文件库”。保存后的文档可在项目的“文件库”中查看，并在以后新建任务时直接勾选复用，不需要重新查找文件。',
      ],
      note: '一次性文档只属于当前任务；文件库按项目隔离，相同内容在一个项目内只保存一份。',
      links: [{ label: '打开文件库', to: '/projects?view=library' }],
    },
  },
  {
    id: 'code-standard-library',
    category: 'tasks',
    question: '项目开发规范库如何关联到任务？',
    keywords: ['开发规范', '代码规范', '文件库', '默认引用', '版本', '任务快照', '复制'],
    answer: {
      paragraphs: [
        '在“项目 → 文件库 → 开发规范”上传 Markdown、文本或结构化文本规范。支持启用、停用、默认引用、版本记录、归档，以及从其他项目复制。',
        '新建任务时会自动勾选默认规范。任务创建后保存具体版本，后续更新规范不会改变已有任务；派发前会校验文件与 SHA-256，执行时把规范正文作为强制约束交给 Codex。',
      ],
      note: '开发规范只接受可稳定读取的 UTF-8 文本。规范文件缺失或摘要不一致时任务会被拒绝派发，不会静默跳过。',
      links: [{ label: '打开文件库', to: '/projects?view=library' }],
    },
  },
  {
    id: 'test-case-types',
    category: 'testing',
    question: 'Web 测试和接口测试有什么区别？',
    keywords: ['测试用例', 'Web', '接口', 'API', 'Playwright', 'Newman', 'Pytest'],
    answer: {
      paragraphs: [
        'Web 测试和接口测试首先是管理分类：Web 用例通常验证页面、交互和浏览器行为，接口用例通常验证 HTTP 接口、数据契约或业务链路。',
        '平台不内置固定测试框架。实际怎么运行由用例里的执行命令决定，可以使用项目已经安装的 Playwright、Newman、Pytest、JUnit 或其他工具。',
      ],
      links: [{ label: '打开测试用例', to: '/test-cases' }],
    },
  },
  {
    id: 'test-case-create',
    category: 'testing',
    question: '如何新建并上传测试用例？',
    keywords: ['新建测试用例', '上传', '脚本', '命令', 'REGENT_TEST_FILE', '512KB'],
    answer: {
      paragraphs: [
        '选择项目、类型和名称，再填写一条执行命令。执行命令会在项目根目录运行，并自动继承该项目的环境信息。',
        '可以额外上传一个 UTF-8 测试文件，单文件上限 512 KB。执行时平台会把脚本还原到本次运行的临时目录，并通过环境变量 REGENT_TEST_FILE 提供路径，不会污染项目工作区。',
      ],
      steps: [
        '新建 Web 或接口测试用例。',
        '填写项目里真实可用的测试命令。',
        '需要脚本时上传 .sh、.js、.py 或其他文本文件。',
        '保存后确认用例处于启用状态。',
      ],
      note: '如果只上传 .sh、.js 或 .py 且没有填写命令，平台会按扩展名推断执行方式。密码和 Token 应放在环境信息中，不要写进脚本。',
      links: [{ label: '新建测试用例', to: '/test-cases' }],
    },
  },
  {
    id: 'test-case-binding',
    category: 'testing',
    question: '测试用例什么时候执行，如何手动或绑定执行？',
    keywords: ['绑定', '任务完成', '自动执行', '手动执行', '批量执行', '最多10个'],
    answer: {
      paragraphs: [
        '在任务详情的“测试用例”卡片中选择用例，最多绑定 10 条。任务进入执行中、待批准或已完成状态后不能再改绑，需要重新执行的任务可以先重试或修改绑定。',
        '绑定的用例只会在任务成功完成（DONE）后自动执行；任务失败或被终止时不会跑测试。测试结果也不会反过来改变任务状态。',
        '如果只想立即验证一次，可以在“测试用例”菜单直接执行单条用例，或勾选多条后批量执行。手动执行不依赖任务，但同样会保存完整结果。',
      ],
      steps: [
        '自动执行：在任务未开始前绑定用例，任务成功完成后等待结果。',
        '单条执行：在测试用例行点“立即执行”。',
        '批量执行：勾选多条已启用用例，再点“执行选中”。',
        '在“查看执行记录”中核对触发方式、退出码和输出。',
      ],
      links: [
        { label: '打开测试用例', to: '/test-cases' },
        { label: '前往任务页', to: '/tasks' },
      ],
    },
  },
  {
    id: 'test-case-results',
    category: 'testing',
    question: '测试结果里的通过、失败、超时和跳过分别是什么意思？',
    keywords: ['测试失败', '执行记录', 'SUCCESS', 'FAILED', 'TIMEOUT', 'SKIPPED', '中断'],
    answer: {
      paragraphs: [
        '通过表示进程退出码为 0；失败表示进程以非零退出码结束；超时表示超过平台配置的测试时限后被终止；已跳过表示执行时用例已停用或删除。',
        '每条测试都会单独保存触发方式、退出码、摘要和输出。任务完成触发的记录会显示任务号，菜单手动执行的记录显示“手动执行”。测试失败不会回滚已完成任务，也不会自动重跑；平台重启时，尚未收场的测试会标记为已中断。',
      ],
      note: '先看失败测试的输出和环境信息，确认是断言失败、依赖缺失、端口不通还是超时，再判断应该修代码、改用例还是调整环境。',
      links: [{ label: '查看测试用例记录', to: '/test-cases' }],
    },
  },
  {
    id: 'review-per-diff',
    category: 'testing',
    question: '审阅 Diff 时应该看什么，如何逐项通过、要求修改或驳回？',
    keywords: ['审阅', 'Diff', 'diff', '通过本项', '本项要求修改', '驳回本项', '待决策', '已通过', '整单通过'],
    answer: {
      paragraphs: [
        '每个文件会拆成独立的审阅项，不再把多个文件混成一个大单元。页面先展示“关键总结”和“需要你确认”的问题，只有需要核对细节时再展开 Diff。',
        '待决策使用蓝色圆点和橙色徽标，已通过为绿色，要求修改为橙色，驳回为红色。颜色只表示当前结论，不替代具体文字。',
        '每个审阅项可以单独执行“通过本项”“本项要求修改”或“驳回本项”。要求修改和驳回必须填写原因。',
        '整单通过前，所有必看项必须先逐项通过，且不能存在被要求修改或驳回的项目。整单要求修改时，平台会汇总逐项意见创建后续修复任务。',
      ],
      steps: [
        '先看高风险项的关键总结和需要确认的问题。',
        '需要核对代码时展开关键 Diff，或点“打开项目代码”在本机编辑器中查看。',
        '确认无误后点“通过本项”；有问题时选择要求修改或驳回并填写原因。',
        '必看项全部通过后，再点“通过整单”。',
      ],
      note: '低风险项可以按需决策，不要求逐行阅读完整 Diff。',
      links: [{ label: '进入任务页', to: '/tasks' }],
    },
  },
  {
    id: 'decision-levels',
    category: 'decisions',
    question: 'L0 到 L3 的决策等级是什么意思？',
    keywords: ['L0', 'L1', 'L2', 'L3', '分级', '等级', '风险'],
    answer: {
      paragraphs: [
        'L0 是完全自主的小改动，例如文案、日志、注释和测试；L1 包括命名、内部结构和小范围错误提示调整，仍由 AI 自主决定并留痕。',
        'L2 包括接口签名、库表字段、跨模块改动和新增依赖，需要人工批准；L3 包括删数据、权限鉴权、发布打包和密钥证书，同样强阻塞并等待人工处理。',
      ],
      note: '项目阈值决定哪些等级可以由 AI 自行处理。实际等级还会与规则命中结果比较，始终就高不就低。',
    },
  },
  {
    id: 'why-blocked',
    category: 'decisions',
    question: '任务为什么会突然进入“待批准”？',
    keywords: ['阻塞', '待批准', 'BLOCKED', '升级', '人工', '阈值'],
    answer: {
      paragraphs: [
        '执行器发现当前动作存在需要人确认的选择，并且等级超过项目允许的自主范围时，任务会停在“待批准”。',
        '规则命中等级和 AI 自评等级会取较高者。例如 AI 认为是 L0，但改动命中发布规则，最终仍会按 L3 处理。',
      ],
      links: [{ label: '处理待批决策', to: '/decisions' }],
    },
  },
  {
    id: 'approve-reject',
    category: 'decisions',
    question: '批准和驳回分别会发生什么？',
    keywords: ['批准', '驳回', '续跑', '终止', '审批'],
    answer: {
      paragraphs: [
        '批准后，任务会带着已有上下文从断点继续执行，不会从头重跑。你可以在批准时补充建议，或追加图片作为执行输入。',
        '驳回后任务会终止。理由可选，填写后会写入决策留痕，方便之后回看当时为何否掉。',
      ],
    },
  },
  {
    id: 'resolved-decisions',
    category: 'decisions',
    question: '为什么决策页默认看不到 L0/L1 自主决断？',
    keywords: ['自主决断', '留痕', '已处理', '看不到', 'L0', 'L1'],
    answer: {
      paragraphs: [
        '决策页默认只显示需要人处理的待批事项，避免低风险自主决断淹没真正需要判断的记录。',
        '切到“已处理”后，可以查看 AI 自主决断以及人工批准或驳回的完整留痕。',
      ],
    },
  },
  {
    id: 'project-fields',
    category: 'configuration',
    question: '新建研发项目时为什么不再填写路径、版本和决策阈值？',
    keywords: ['项目', '路径', '阈值', '版本', '新建', '工作区', '标识'],
    answer: {
      paragraphs: [
        '研发项目先创建业务主体，只需要所属组织和项目名称。项目标识由平台自动生成，不需要人工维护。',
        '代码目录和代码仓库属于开发成员的本机工作区，不同开发者可以绑定不同路径；在完成绑定前，立项、需求评审和设计仍然可用，但任务派发、AI 分析、部署和项目摘要会被阻止。',
        '决策阈值与版本来源属于后续执行配置，新项目使用安全的默认值，不再要求在注册时填写。',
      ],
      note: '平台会在派发前明确提示“项目尚未绑定本机代码目录”，不会使用错误目录执行任务。',
      links: [{ label: '新建或查看项目', to: '/projects' }],
    },
  },
  {
    id: 'workflow-templates',
    category: 'configuration',
    question: '研发流程模板在哪里配置，项目如何绑定流程？',
    keywords: ['研发流程', '流程模板', '阶段', '审批角色', '绑定流程', '全局模板'],
    answer: {
      paragraphs: [
        '组织所有者或组织管理员可以在设置页维护全局研发流程模板。模板可以包含多个阶段，并为每个阶段指定版本级或需求级范围、审批角色和说明。',
        '已发布模板不会原地修改。调整流程时复制新版本，旧版本自动变为“已替代”，已经绑定旧版本的项目和产品版本继续使用原快照。',
        '新建项目时从当前发布版本中选择一套流程。项目创建后，后续创建的产品版本都会固定使用这套模板快照。',
      ],
      steps: [
        '进入“设置 → 研发流程”。',
        '新建模板，或在既有模板上点击“复制新版本”。',
        '配置阶段顺序、范围和审批角色并发布。',
        '新建项目时选择刚发布的研发流程。',
      ],
      note: '已被项目或产品版本使用的模板不能停用；至少需要保留一个可用于新项目的发布模板。',
      links: [{ label: '配置研发流程', to: '/settings/workflow' }],
    },
  },
  {
    id: 'project-version',
    category: 'configuration',
    question: '项目版本应该怎么设置？',
    keywords: ['项目版本', 'Git Tag', '发布版本', '备用版本', '手动版本', '部署版本'],
    answer: {
      paragraphs: [
        '项目设置支持两种版本来源：Git Tag 和手动固定版本。推荐使用 Git Tag，让版本始终与当前代码提交保持一致。',
        'Git Tag 模式下可以再填一个备用版本：当前提交没有 Tag 时使用。手动模式必须填写项目版本，适用于暂时无法维护 Tag 的项目。',
        '部署确认页会显示本次解析出的版本，部署记录会保存版本、来源、任务和审阅信息，后续可以按版本追溯。',
      ],
      steps: [
        '在项目页编辑项目，选择版本来源。',
        'Git Tag 模式可以直接提交一个形如 v1.2.3 的 Tag。',
        '没有 Tag 的项目填写备用版本，或切换为手动固定版本。',
        '发起部署前核对确认页显示的项目版本。',
      ],
      note: '版本只允许字母、数字、点、下划线、加号和连字符，最长 64 个字符。',
      links: [{ label: '打开项目页', to: '/projects' }],
    },
  },
  {
    id: 'open-project-code',
    category: 'configuration',
    question: '如何用本机编辑器打开项目代码？',
    keywords: ['打开代码', 'VS Code', 'Cursor', 'IntelliJ IDEA', '编辑器', '定位行号', '项目代码'],
    answer: {
      paragraphs: [
        '项目列表每行都有“打开项目代码”图标，项目详情中也有独立按钮。审阅工作区会把当前 Diff 的文件和行号一起传给编辑器，Git 提交详情可以打开整个项目。',
        '平台自动检测 VS Code、Cursor 和 IntelliJ IDEA。检测到 CLI 时会定位到具体文件和行；只有 macOS 应用时，会打开项目目录。',
        '编辑器调用采用固定白名单，前端不能传任意命令；代码文件必须位于当前账号所属项目内。',
      ],
      steps: [
        '在项目页、审阅工作区或 Git 提交详情点击“打开项目代码”。',
        '从检测到的编辑器中选择一个。',
        '如果没有检测到编辑器，安装 VS Code、Cursor 或 IntelliJ IDEA 后点“重新检测”。',
      ],
      note: '打开代码调用的是运行 Regent 后端的这台电脑上的编辑器，而不是手机或另一台电脑。',
      links: [{ label: '打开项目页', to: '/projects' }],
    },
  },
  {
    id: 'git-commit-flow',
    category: 'configuration',
    question: '审阅通过后如何生成并提交 Git 说明？',
    keywords: ['Git 提交', '提交说明', 'Bug 编号', '6位', '提交规范', '102008', '提交模板'],
    answer: {
      paragraphs: [
        '审阅整单通过后，任务详情会出现“提交 Git”。平台会根据任务是否关联 Bug，按企业规范自动生成可编辑的提交说明。',
        '关联 Bug 时自动生成 type(6位编号): 中文说明，例如 feat(102008): 优化告警记录处理逻辑；未关联 Bug 时自动生成 type: 中文说明。用户不需要填写 Bug 编号。',
        '设置页的“Git 提交”分类可以调整默认类型、标题模板、正文模板、校验正则和失败提示。',
        '平台只提交当前审阅快照覆盖的文件；未审阅改动和已有暂存内容不会被混入提交。',
      ],
      steps: [
        '完成逐项审阅并点击“通过整单”。',
        '检查自动生成的提交说明，按需微调后直接提交。',
        '确认分支和文件范围后点“提交 Git”。',
        '成功后任务详情会显示提交说明、分支和短 SHA。',
      ],
      links: [
        { label: 'Git 提交设置', to: '/settings/git' },
        { label: '进入任务页', to: '/tasks' },
      ],
    },
  },
  {
    id: 'environment-entries',
    category: 'configuration',
    question: '环境信息会在什么时候提供给执行器？',
    keywords: ['环境', '环境变量', '全局', '项目', '数据库地址', '配置', '自动生成', '名称', '密码', '脱敏'],
    answer: {
      paragraphs: [
        '环境键由平台自动生成（ENV_001、ENV_002……），不需要也不要手工定义。名称最多 64 个字符，例如“生产 MySQL 主库只读连接”。',
        '环境信息可以全局维护，也可以绑定到单个项目。标记为密码的值会在列表和详情中默认脱敏；普通 HTTP / HTTPS 地址可以直接点击访问。',
      ],
      note: '密码值只应在可信项目和执行器中使用；点击眼睛可以临时查看。',
      links: [{ label: '管理环境信息', to: '/environments' }],
    },
  },
  {
    id: 'plugins-and-plans',
    category: 'configuration',
    question: '插件和计划分别适合什么场景？',
    keywords: ['插件', '能力包', '计划', '定时', '批次', '复用'],
    answer: {
      paragraphs: [
        '插件库用于保存已经验证过的代码、协议或接入步骤。任务会固定绑定某个插件版本，避免后续编辑悄悄改变正在复用的内容。',
        '计划用于把待执行或排队中的任务组成批次，到设定时间统一执行，并保留本轮结果。',
      ],
      links: [
        { label: '查看插件库', to: '/plugins' },
        { label: '查看计划', to: '/plans' },
      ],
    },
  },
  {
    id: 'deployment-setup',
    category: 'configuration',
    question: '部署服务器怎么复用，脚本里的连接信息怎么填？',
    keywords: ['部署', '项目', '服务器', '复用', 'SSH', '脚本', '密码', '密钥', '日志', '环境变量', 'AI', 'Codex', '项目文件夹', '生成'],
    answer: {
      paragraphs: [
        '先在“服务器”页按项目录入主机、端口、登录用户和认证方式；也可以从部署弹框里直接新建。多个部署脚本可以复用同一台服务器。',
        '部署配置可以选择服务器、填写部署名称和远程目录，再上传 UTF-8 脚本；新建部署会默认沿用顶栏当前选中的项目。项目暂无服务器时可在弹框里直接新建，不会自动改到其他项目。也可以在新建部署时选择项目文件夹，让 Codex 只读分析项目，并优先参考 deploy/、script/、scripts/ 里已有部署脚本生成新脚本。',
        '执行方式分两种：远端脚本沿用 SSH stdin；项目发布脚本则从项目根目录直接调用配置的 deploy.sh 一类脚本，由它完成本机构建、上传、重启和健康检查。上传双模式 deploy.sh 时，平台会根据内容自动切到项目发布脚本。',
        '项目发布脚本会收到 -H 服务器、-P 端口、-u 用户、-r 远程安装根目录；密码只通过 REGENT_SERVER_PASSWORD 环境变量传入，不会进入命令行或日志。上传内容必须与项目内脚本文件完全一致。',
        '远端脚本会在远程目录下通过 bash 执行，平台在本机拉起 OpenSSH，不会把脚本先上传到服务器。生成或上传的脚本都会先经过同一套规范检测。',
        '远程目录由配置统一提供，平台执行前会自动创建并进入该目录；脚本里不要再写死 cd /...，需要读取路径时使用 $REGENT_DEPLOY_DIRECTORY。',
        '上传脚本后会自动检测 bash 头、fail-fast 设置、写死目录、交互输入和重复 SSH 连接；不通过时会指出具体行号，修复前不能保存或执行。',
        '密钥认证使用运行后端那台机器的 ssh-agent 或默认私钥；密码属于服务器资源，加密保存且不会通过查询接口回传，也不会写入执行日志。',
      ],
      note: '部署日志写入 ~/.regent/deployments/runs/，可在部署列表右侧的日志按钮中查看。',
      links: [
        { label: '管理服务器', to: '/servers' },
        { label: '打开部署', to: '/deployments' },
      ],
    },
  },
  {
    id: 'run-record',
    category: 'records',
    question: '运行记录里的“执行对话”和“技术日志”有什么区别？',
    keywords: ['运行记录', '事件', '技术日志', 'JSONL', '执行对话', '执行细节', '排错'],
    answer: {
      paragraphs: [
        '“执行对话”只展示 Codex 的说明、结论和需要你关注的事项，并按 Codex 常见的 Markdown 样式呈现表格、列表和代码。',
        '思考过程、命令输出、文件改动和原始事件默认收起。需要核对执行细节时点“显示执行细节”；需要排查解析问题时再打开“技术日志”。',
      ],
    },
  },
  {
    id: 'runtime-update',
    category: 'records',
    question: '客户端更新为什么需要重启，更新失败怎么办？',
    keywords: ['更新', 'Runtime', '版本号', '回滚', '死循环', '重启', '下载', 'R100B'],
    answer: {
      paragraphs: [
        '新安装的桌面客户端默认连接 `https://ichuang.top/regent/decisions`，不需要填写服务器地址；需要连接自建服务时，才从菜单打开“切换服务地址”。',
        '桌面客户端由固定客户端壳和 Runtime 组成。普通业务更新只下载较小的 Runtime，校验通过后安装并在重启时切换到新版本；完整安装包只在客户端壳需要升级时下载。',
        '当前版本号采用 `Regent_V主版本.次版本_发布码` 格式，例如 `Regent_V1.0.4_R100B014`。其中 `R100B014` 是本次发布的客户端发布码，不是 Git 分支名。',
        'Runtime 启动失败时，客户端会尝试自动回到上一个有效版本。更新后如果仍看到旧提示，先完全退出客户端再重新打开；不要反复点击更新，必要时从下载页重新获取最新客户端。',
      ],
      steps: [
        '确认网络可以访问 Regent 服务，并保留足够的临时磁盘空间。',
        '点击“下载并安装”，等待下载、校验和自动重启完成。',
        '如果启动失败，客户端会自动回滚；重新打开后查看是否仍提示同一发布码。',
        '仍无法启动时，从下载页重新获取最新客户端，不要手工修改 Runtime 目录。',
      ],
      note: '更新包会校验 SHA-256 和签名，安装内容必须与发布清单一致。不要把“更新窗口关闭”误认为更新完成，安装完成后客户端会自动重启。',
      links: [{ label: '打开下载页', to: '/download' }],
    },
  },
  {
    id: 'deployment-remote-directory',
    category: 'configuration',
    question: '上传脚本后远程目录为什么还是空的？',
    keywords: ['部署', '远程目录', 'REGENT_DEPLOY_DIRECTORY', '脚本', '目录', '自动识别'],
    answer: {
      paragraphs: [
        '自动识别只读取脚本里写死的绝对路径或 ~ 路径。按平台规范编写的脚本使用 $REGENT_DEPLOY_DIRECTORY，源码里本来就没有可推断的路径。',
        '正确做法是在“服务器”设置里填写一次默认远程目录（通常是 package.json 所在的项目根目录），新建部署选中这台服务器时会自动带出。也可以在当前部署里手动覆盖。',
        '/usr/setup/.../static/... 这类 web_dir 是发布目标，不是构建工作目录，不要拿它当远程目录。',
      ],
      note: '服务器默认目录和部署远程目录都为空时，AI 生成与保存会被禁用。',
      links: [{ label: '打开部署', to: '/deployments' }],
    },
  },
  {
    id: 'deployment-failure',
    category: 'records',
    question: '部署失败后应该先检查哪一步？',
    keywords: ['部署失败', '发布失败', '服务器', '脚本', '远程目录', '权限', '健康检查', '回滚'],
    answer: {
      paragraphs: [
        '先按阶段定位：项目和环境预检、服务器连接、脚本规范、产物上传、远端执行、服务重启和健康检查。不要只根据最后一行错误直接重试。',
        '如果失败发生在重启或健康检查阶段，先确认线上是否仍在运行上一版本，再决定修复后重试，或按项目已有回滚方案恢复。涉及生产环境时不要连续重复发布。',
      ],
      steps: [
        '查看失败阶段和具体日志，确认错误来自配置、网络、脚本还是服务启动。',
        '检查服务器、远程目录、脚本目标目录和凭据是否有权限。',
        '确认构建产物与脚本期望的目录和文件名一致。',
        '修复后重新执行预检，再发起部署；需要时使用上一成功版本回滚。',
      ],
      note: '密码只应通过服务器凭据或环境变量传递，不要为了“先跑通”把密码写入脚本、命令行或日志。',
      links: [{ label: '打开部署', to: '/deployments' }],
    },
  },
  {
    id: 'failed-task',
    category: 'records',
    question: '任务失败后应该先看什么？',
    keywords: ['失败', 'FAILED', '排障', '错误', '超时', '重试'],
    answer: {
      paragraphs: [
        '先打开对应运行记录，确认是执行器报告失败、出参无法识别、进程异常退出，还是超过执行时限。',
        '再看“出参原文”和原始事件定位具体原因。修正项目环境或任务指令后，再使用重试，避免同一失败原样重复消耗执行资源。',
      ],
      steps: [
        '确认失败发生在哪一次运行尝试。',
        '查看执行对话末尾和退出原因。',
        '必要时切到原始事件核对完整错误。',
        '修正路径、环境信息或任务指令后再重试。',
      ],
      links: [{ label: '查看失败任务', to: '/tasks?status=FAILED' }],
    },
  },
  {
    id: 'bug-flow',
    category: 'records',
    question: '执行中发现的缺陷如何流转？',
    keywords: ['Bug', '缺陷', '修复', '验证', '关闭', 'FIXED'],
    answer: {
      paragraphs: [
        '执行器可以把发现的问题上报为缺陷单，也可以人工录入。缺陷可以派生一个修复任务，并把状态推进到修复中。',
        '修复任务完成只代表“已修复待验证”，不代表问题已经关闭。只有人工验证通过后，缺陷才会进入已关闭。',
      ],
      links: [{ label: '打开缺陷单', to: '/bugs' }],
    },
  },
  {
    id: 'external-executor',
    category: 'records',
    question: '运行记录里的“外部执行者”是什么？',
    keywords: ['外部执行者', 'agent', '租约', 'codex', '执行器'],
    answer: {
      paragraphs: [
        '平台可以自己拉起本机 codex，也可以让外部工具按协议领取任务并上报结果。两者共用同一套决策与结算规则。',
        '外部执行者依靠心跳维持租约。租约过期后，平台会把它视为失联并允许后续回收，不会让任务永久停在执行中。',
      ],
    },
  },
  {
    id: 'git-commit-records',
    category: 'records',
    question: '在哪里查看 Git 提交记录，失败记录会保留吗？',
    keywords: ['Git 提交记录', 'Commit SHA', '提交失败', '交付', '提交历史', '审计'],
    answer: {
      paragraphs: [
        '任务详情会显示最近一次 Git 提交摘要，包括状态、提交说明、分支、短 SHA、失败原因和提交次数入口。',
        '交付页的“Git 提交”标签可以按项目、状态和关键字查询。关键字支持任务标题、Bug 编号、提交说明和 Commit SHA。',
        '每次提交尝试都会单独保存。成功和失败都不会覆盖历史；失败后修正问题，可以在任务审阅页重新发起新的提交尝试。',
        '提交详情会展示完整 SHA、来源任务、审阅快照、仓库路径、文件清单和失败信息。',
      ],
      steps: [
        '在任务详情点击最近提交卡片，或打开“交付 → Git 提交”。',
        '使用状态和关键字缩小范围。',
        '点击一行查看来源任务、文件清单和完整 Commit SHA。',
      ],
      note: '平台管理的是自己发起和审计的 Git 提交，不会镜像远端仓库的完整 Git 历史。',
      links: [
        { label: '打开 Git 提交记录', to: '/delivery?view=git-commits' },
        { label: '进入任务页', to: '/tasks' },
      ],
    },
  },
  {
    id: 'registration-login',
    category: 'account',
    question: '如何注册和保持登录？',
    keywords: ['注册', '登录', '验证码', '密码', '保持登录', '会话'],
    answer: {
      paragraphs: [
        '注册使用邮箱验证码，验证码 5 分钟内有效，同一邮箱 60 秒内不能重复发送。密码至少 8 个字符。',
        '登录时勾选“保持登录”可以把会话延长到较长时间；在公共设备上不建议勾选。',
      ],
    },
  },
  {
    id: 'sensitive-data',
    category: 'account',
    question: '密码、Token 等敏感信息应该放在哪里？',
    keywords: ['安全', '密码', 'Token', '密钥', '证书', '凭据', '环境信息'],
    answer: {
      paragraphs: [
        '部署凭据、数据库密码和邮件授权码应放在被忽略的本地配置或环境变量中，不要写进代码、文档或提交信息。',
        '执行所需的环境信息可以通过环境页维护。它仍属于敏感数据，只应授权给可信项目和执行器，不要写进任务指令或插件正文。',
      ],
      links: [{ label: '管理环境信息', to: '/environments' }],
    },
  },
]

const CATEGORY_LABELS = Object.fromEntries(
  HELP_CATEGORIES.map((category) => [category.id, category.label]),
) as Record<HelpCategoryId, string>

export function helpCategoryLabel(id: HelpCategoryId): string {
  return CATEGORY_LABELS[id]
}

export function filterHelpTopics(
  topics: readonly HelpTopic[],
  query: string,
  category: HelpFilter = 'all',
): readonly HelpTopic[] {
  const normalizedQuery = query.trim().normalize('NFKC').toLocaleLowerCase('zh-CN')
  return topics.filter((topic) => {
    if (category !== 'all' && topic.category !== category) return false
    if (!normalizedQuery) return true

    const searchable = [
      topic.question,
      helpCategoryLabel(topic.category),
      ...topic.keywords,
      ...topic.answer.paragraphs,
      ...(topic.answer.steps ?? []),
      topic.answer.note ?? '',
    ]
      .join('\n')
      .normalize('NFKC')
      .toLocaleLowerCase('zh-CN')

    return searchable.includes(normalizedQuery)
  })
}
