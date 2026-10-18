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
    title: '注册项目',
    description: '项目决定执行目录和人工决策阈值。路径必须是后端所在机器上真实存在的工作区。',
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
    description: '待执行任务可以手动派发给本机 codex；启用自动调度时，它也会按优先级自动领取。',
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
        '先注册一个项目，再在任务页录入任务。项目提供执行目录和决策阈值，任务则承载这次要完成的具体工作。',
        '任务开始执行后，通常只需要在工作台或决策页处理真正需要人拍板的事项。',
      ],
      steps: [
        '注册项目并确认本机工作区路径可读。',
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
        '当前界面直接录入的是任务。需求单录入和“需求拆解为任务”的完整入口暂未开放，因此现在不需要先创建需求。',
      ],
      note: '界面会把人工录入的任务来源标为“人工”，不会伪装成已经过需求拆解。',
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
        '在新建任务的指令框上方点击“语音输入”，选择说话语言后开始听写，识别结果会追加到当前指令中，不会覆盖已经输入的文字。',
        '普通话直接写入中文；English、日本語和 한국어 会尝试使用浏览器翻译能力转成中文。若浏览器不支持翻译，会保留识别原文并给出提示。',
      ],
      note: '语音输入只在 HTTPS 或本机 localhost 下可用，推荐最新版 Chrome 或 Edge。音频不会上传给 Regent 后端，但浏览器自身的语音识别或翻译服务可能联网处理。',
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
        '待执行表示尚未派发；排队中表示已进入执行队列但还没有拿到执行位；执行中表示执行器正在工作。',
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
        '平台同时运行的任务数受执行设置中的并发上限控制，可在设置页调整。调低上限不会打断正在运行的任务，只会让后续任务继续等待。',
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
        '驳回后任务会终止。驳回理由必须填写，并会写入决策留痕，避免以后只看到“被否掉”却不知道原因。',
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
    question: '注册项目时，路径和决策阈值怎么填？',
    keywords: ['项目', '路径', '阈值', '注册', '工作区', '标识'],
    answer: {
      paragraphs: [
        '路径必须是运行后端那台机器上的绝对路径，并且目录真实存在。执行器和项目工作区应在同一台机器上工作。',
        '决策阈值表示项目允许 AI 自主处理的最高等级。高于阈值的决策会挂起等待人工处理，拿不准时应选择更保守的等级。',
      ],
      note: '项目标识用于稳定引用，注册后不会随名称变化。',
      links: [{ label: '注册或查看项目', to: '/projects' }],
    },
  },
  {
    id: 'environment-entries',
    category: 'configuration',
    question: '环境信息会在什么时候提供给执行器？',
    keywords: ['环境', '环境变量', '全局', '项目', '数据库地址', '配置'],
    answer: {
      paragraphs: [
        '环境信息可以全局维护，也可以绑定到单个项目。两者存在同名键时，项目级信息覆盖全局信息。',
        '任务执行时，平台会把当前生效的环境信息交给执行器；可持久化的任务指令只记录键与说明，不写入具体值。',
      ],
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
    id: 'run-record',
    category: 'records',
    question: '运行记录里的“执行过程”和“原始事件”有什么区别？',
    keywords: ['运行记录', '事件', '原始事件', 'JSONL', '执行过程', '排错'],
    answer: {
      paragraphs: [
        '“执行过程”把事件流整理成更容易阅读的动作、命令、输出和结论，适合日常核对。',
        '“原始事件”保留执行器返回的逐条数据，适合排查解析或平台行为问题；两者不一致时以原始事件为准。',
      ],
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
        '查看执行过程末尾和退出原因。',
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
