import { ref } from 'vue'

/** 轻提示（与原型 #toast 视觉一致） */
export const toastState = ref({ text: '', show: false })

let timer = null

export function toast(message) {
  toastState.value.text = message
  toastState.value.show = true
  clearTimeout(timer)
  timer = setTimeout(() => {
    toastState.value.show = false
  }, 1900)
}
