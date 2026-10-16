import { computed, onUnmounted, ref, watch, type Ref } from 'vue'

/*
 * 浮层之间的层级。
 *
 * 谁盖在谁上面**不能靠 DOM 顺序**：本站每个浮层都 teleport 到 body，插入点在「组件挂载的
 * 那一刻」就定死在 body 的某个位置上，之后开开关关都不会挪。挂在 App 上的注册项目弹框因此
 * 永远排在路由页面里的弹框前面 —— 从新建任务里点「注册项目」，它先渲染、再被后者的遮罩整块
 * 盖住（实测面板上每一点 elementFromPoint 都命中那张遮罩）。反过来，弹框里的下拉框又会被
 * 自己弹框的遮罩压住，同一个病根。
 *
 * 按「打开顺序」发号两个问题一起解决，也符合直觉：后开的浮层在上面，就像 macOS 里新打开的
 * 对话框一定在最前面。所以弹框、下拉框、气泡、菜单都从这里取 z-index，别各自写死 z-50。
 *
 * 区间：50 起、每层 +1、封顶 59。ToastHost 固定在 60，通知永远在最上面，不被浮层吃掉 ——
 * 它要是被盖住，人就永远看不到「保存失败」了。嵌套超过 10 层时同号浮层退回 DOM 顺序决定，
 * 不会坏，只是不再精确。
 *
 * Tooltip 不在其中：它是 pointer-events-none 的纯装饰，而且弹框一开，body 上的
 * pointer-events 就被 reka-ui 关掉，鼠标根本落不到需要提示的按钮上 —— 它没有机会出现在
 * 弹框上面，也就没有层级可争。
 */
const BASE_Z = 50
const MAX_Z = 59

/** 当前开着的浮层，按打开先后排列。只用来发号，不参与任何交互逻辑 */
const layers = ref<number[]>([])
let sequence = 0

export function useOverlayLayer(open: Ref<boolean>) {
  // 号只发不回收：用数组下标的话，同时开关多个浮层会把别人的号顶掉
  const id = ++sequence

  const release = () => {
    const index = layers.value.indexOf(id)
    if (index >= 0) layers.value.splice(index, 1)
  }

  watch(
    open,
    (value) => {
      if (!value) {
        release()
        return
      }
      if (!layers.value.includes(id)) layers.value.push(id)
    },
    { immediate: true },
  )

  // 没关就被销毁（切页、父组件 v-if）也得把号交回去，否则后面的浮层白爬一层
  onUnmounted(release)

  return {
    zIndex: computed(() => Math.min(BASE_Z + Math.max(layers.value.indexOf(id), 0), MAX_Z)),
  }
}
