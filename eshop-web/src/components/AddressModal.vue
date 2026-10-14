<template>
  <div class="mask" :class="{ show: visible }" @click="close">
    <div class="modal" @click.stop>
      <div style="font-size:16px;font-weight:600;margin-bottom:16px">新增收货地址</div>
      <div class="fld"><label>收货人</label><input v-model.trim="form.receiverName" placeholder="收货人姓名" /></div>
      <div class="fld"><label>手机号</label><input v-model.trim="form.receiverPhone" placeholder="11 位手机号" /></div>
      <div class="fld">
        <label>所在地区</label>
        <div class="fld-row">
          <div class="fld"><input v-model.trim="form.province" placeholder="省" /></div>
          <div class="fld"><input v-model.trim="form.city" placeholder="市" /></div>
          <div class="fld"><input v-model.trim="form.district" placeholder="区/县" /></div>
        </div>
      </div>
      <div class="fld"><label>详细地址</label><input v-model.trim="form.detailAddress" placeholder="街道、门牌号等" /></div>
      <label class="check-line"><input type="checkbox" v-model="form.isDefault" /> 设为默认地址</label>
      <div style="display:flex;gap:10px;margin-top:16px">
        <button class="btn btn-ghost" style="flex:1;height:42px" @click="close">取消</button>
        <button class="btn btn-primary" style="flex:1;height:42px" :disabled="saving" @click="save">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { addressApi } from '../api'
import { toast } from '../utils/toast'

const emit = defineEmits(['saved'])

const visible = ref(false)
const saving = ref(false)
const form = reactive({
  receiverName: '',
  receiverPhone: '',
  province: '',
  city: '',
  district: '',
  detailAddress: '',
  isDefault: false
})

function open() {
  visible.value = true
}

function close() {
  visible.value = false
}

async function save() {
  if (!form.receiverName || !form.receiverPhone || !form.detailAddress) {
    return toast('请填写收货人、手机号和详细地址')
  }
  if (!/^1\d{10}$/.test(form.receiverPhone)) return toast('手机号格式不正确')
  saving.value = true
  try {
    const id = await addressApi.create({ ...form })
    toast('地址已保存')
    visible.value = false
    emit('saved', id)
    Object.assign(form, {
      receiverName: '',
      receiverPhone: '',
      province: '',
      city: '',
      district: '',
      detailAddress: '',
      isDefault: false
    })
  } catch (e) {
    /* 业务错误已由拦截器提示 */
  } finally {
    saving.value = false
  }
}

defineExpose({ open })
</script>
