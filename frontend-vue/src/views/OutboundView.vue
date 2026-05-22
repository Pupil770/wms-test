<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createOutboundOrder,
  getProducts,
  getWarehouses,
  getLocations,
  type Product,
  type Warehouse,
  type Location,
} from '@/api'

const customerName = ref('')
const items = ref<Array<{
  productId: number | undefined
  quantity: number
  locationCode: string
  warehouseId: number | undefined
}>>([])
const submitting = ref(false)
const loading = ref(true)

const products = ref<Product[]>([])
const warehouses = ref<Warehouse[]>([])
const locationsMap = ref<Record<number, Location[]>>({})

onMounted(async () => {
  try {
    const [pRes, wRes] = await Promise.all([getProducts(), getWarehouses()])
    products.value = pRes.data
    warehouses.value = wRes.data
  } catch {
    ElMessage.error('加载基础数据失败，请刷新重试')
  } finally {
    loading.value = false
  }
})

const loadLocations = async (warehouseId: number) => {
  if (locationsMap.value[warehouseId]) return
  try {
    const res = await getLocations(warehouseId)
    locationsMap.value[warehouseId] = res.data
  } catch {
    ElMessage.error('加载库位失败')
  }
}

const onWarehouseChange = (index: number) => {
  const item = items.value[index]
  item.locationCode = ''
  if (item.warehouseId) {
    loadLocations(item.warehouseId)
  }
}

const addItem = () => {
  items.value.push({
    productId: undefined,
    quantity: 1,
    locationCode: '',
    warehouseId: undefined,
  })
}

const removeItem = (index: number) => {
  items.value.splice(index, 1)
}

const handleSubmit = async () => {
  if (submitting.value) return
  if (!customerName.value.trim()) {
    ElMessage.warning('请输入客户名称')
    return
  }
  if (customerName.value.length > 200) {
    ElMessage.warning('客户名称不能超过200字符')
    return
  }
  if (items.value.length === 0) {
    ElMessage.warning('请添加至少一条出库明细')
    return
  }
  for (let i = 0; i < items.value.length; i++) {
    const item = items.value[i]
    if (!item.productId) {
      ElMessage.warning(`第 ${i + 1} 行请选择商品`)
      return
    }
    if (!item.warehouseId) {
      ElMessage.warning(`第 ${i + 1} 行请选择仓库`)
      return
    }
    if (!item.locationCode) {
      ElMessage.warning(`第 ${i + 1} 行请选择库位`)
      return
    }
    if (!item.quantity || item.quantity < 1) {
      ElMessage.warning(`第 ${i + 1} 行数量必须大于0`)
      return
    }
  }

  submitting.value = true
  try {
    const res = await createOutboundOrder({
      customerName: customerName.value,
      items: items.value.map((item) => ({
        productId: item.productId!,
        quantity: item.quantity,
        locationCode: item.locationCode,
      })),
    })
    ElMessage.success(`出库单创建成功：${res.data.orderNo}`)
    customerName.value = ''
    items.value = []
    locationsMap.value = {}
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '创建失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div v-loading="loading">
    <h3>出库管理</h3>

    <el-form label-width="100px" style="max-width: 900px">
      <el-form-item label="客户名称" required>
        <el-input v-model="customerName" placeholder="请输入客户名称" maxlength="200" show-word-limit style="width: 300px" />
      </el-form-item>

      <el-form-item label="出库明细">
        <el-button type="primary" @click="addItem">+ 添加明细</el-button>
      </el-form-item>
    </el-form>

    <!-- 明细列表 -->
    <el-table :data="items" border style="max-width: 900px" v-if="items.length > 0">
      <el-table-column label="商品" min-width="200">
        <template #default="{ row }">
          <el-select
            v-model="row.productId"
            placeholder="选择商品"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="p in products"
              :key="p.id"
              :label="`${p.name} (${p.sku})`"
              :value="p.id"
            />
          </el-select>
        </template>
      </el-table-column>

      <el-table-column label="仓库" width="180">
        <template #default="{ row, $index }">
          <el-select
            v-model="row.warehouseId"
            placeholder="选择仓库"
            style="width: 100%"
            @change="onWarehouseChange($index)"
          >
            <el-option
              v-for="w in warehouses"
              :key="w.id"
              :label="w.name"
              :value="w.id"
            />
          </el-select>
        </template>
      </el-table-column>

      <el-table-column label="库位" width="180">
        <template #default="{ row }">
          <el-select
            v-model="row.locationCode"
            placeholder="选择库位"
            :disabled="!row.warehouseId"
            style="width: 100%"
          >
            <el-option
              v-for="loc in (locationsMap[row.warehouseId!] || [])"
              :key="loc.code"
              :label="loc.code"
              :value="loc.code"
            />
          </el-select>
        </template>
      </el-table-column>

      <el-table-column label="数量" width="130">
        <template #default="{ row }">
          <el-input-number
            v-model="row.quantity"
            :min="1"
            :max="99999"
            :precision="0"
            :step="1"
            controls-position="right"
            style="width: 100%"
          />
        </template>
      </el-table-column>

      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ $index }">
          <el-button type="danger" size="small" @click="removeItem($index)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div style="margin-top: 16px">
      <el-button
        type="warning"
        size="large"
        :loading="submitting"
        @click="handleSubmit"
        :disabled="items.length === 0"
      >
        提交出库单
      </el-button>
    </div>

    <el-empty v-if="items.length === 0" description="请点击「添加明细」按钮添加出库商品" />
  </div>
</template>
