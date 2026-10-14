<template>
  <div class="pager" v-if="totalPages > 1">
    <span :class="{ disabled: page <= 1 }" @click="go(page - 1)">‹</span>
    <span v-for="p in pages" :key="p" :class="{ on: p === page }" @click="go(p)">{{ p }}</span>
    <span :class="{ disabled: page >= totalPages }" @click="go(page + 1)">›</span>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  page: { type: Number, default: 1 },
  total: { type: Number, default: 0 },
  size: { type: Number, default: 12 }
})
const emit = defineEmits(['change'])

const totalPages = computed(() => Math.max(1, Math.ceil((props.total || 0) / (props.size || 12))))
const pages = computed(() => {
  const arr = []
  for (let i = 1; i <= totalPages.value; i++) arr.push(i)
  return arr
})

function go(p) {
  if (p < 1 || p > totalPages.value || p === props.page) return
  emit('change', p)
}
</script>
