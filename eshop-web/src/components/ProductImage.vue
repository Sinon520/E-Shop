<template>
  <div :class="imgClass" :style="bg">
    <svg viewBox="0 0 64 64" fill="none" :stroke="stroke" stroke-linecap="round" stroke-linejoin="round" v-html="icon"></svg>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { placeholderFor, imageBg, imageStroke } from '../utils/placeholder'

/**
 * 商品占位图：同色系渐变底 + 简笔 SVG 图标（商品图 URL 为空时的统一降级视觉）
 */
const props = defineProps({
  id: { type: [Number, String], default: 0 },
  imgClass: { type: String, default: 'pimg' }
})

const ph = computed(() => placeholderFor(Number(props.id)))
const bg = computed(() => imageBg(ph.value.hue))
const stroke = computed(() => imageStroke(ph.value.hue))
const icon = computed(() => ph.value.icon)
</script>
