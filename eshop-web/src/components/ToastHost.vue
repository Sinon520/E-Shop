<template>
  <div id="toast" :class="{ show: toastState.show }">{{ toastState.text }}</div>
</template>

<script setup>
import { toastState } from '../utils/toast'
</script>
