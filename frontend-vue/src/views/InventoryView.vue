<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getInventory, getWarehouses, type InventoryItem, type Warehouse } from '@/api'

const keyword = ref('')
const warehouseId = ref<number | undefined>()
const loading = ref(false)
const inventoryList = ref<InventoryItem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const warehouses = ref<Warehouse[]>([])

let debounceTimer: ReturnType<typeof setTimeout> | null = null

const loadInventory = async () => {
  loading.value = true
  try {
    const res = await getInventory({
      keyword: keyword.value || undefined,
      warehouseId: warehouseId.value,
      page: page.value,
      pageSize: pageSize.value,
    })
    inventoryList.value = res.data.list
    total.value = res.data.total
  } catch (e: any) {
    ElMessage.error('加载失败: ' + (e.response?.data?.message || e.message))
  } finally {
    loading.value = false
  }
}

const onKeywordInput = () => {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    page.value = 1
    loadInventory()
  }, 400)
}

const onPageChange = (p: number) => {
  page.value = p
  loadInventory()
}

const getRowStyle = ({ row }: { row: InventoryItem }) => {
  if (row.quantity < 10) {
    return { backgroundColor: '#fff0f0', color: '#f56c6c' }
  }
  return {}
}

onMounted(async () => {
  try {
    const wRes = await getWarehouses()
    warehouses.value = wRes.data
  } catch { /* ignore */ }
  loadInventory()
})
</script>

<template>
  <div>
    <h3>库存查询</h3>

    <div style="display: flex; gap: 12px; margin-bottom: 16px">
      <el-input
        v-model="keyword"
        placeholder="搜索商品名称/SKU..."
        style="width: 300px"
        clearable
        @input="onKeywordInput"
        @clear="() => { page = 1; loadInventory() }"
      />
      <el-select
        v-model="warehouseId"
        placeholder="选择仓库"
        clearable
        style="width: 200px"
        @change="() => { page = 1; loadInventory() }"
      >
        <el-option
          v-for="w in warehouses"
          :key="w.id"
          :label="w.name"
          :value="w.id"
        />
      </el-select>
    </div>

    <el-table :data="inventoryList" v-loading="loading" border stripe :row-style="getRowStyle">
      <el-table-column prop="productName" label="商品名称" />
      <el-table-column prop="sku" label="SKU" width="150" />
      <el-table-column prop="locationCode" label="库位编码" width="150" />
      <el-table-column prop="warehouseName" label="仓库" width="120" />
      <el-table-column prop="quantity" label="库存数量" width="100">
        <template #default="{ row }">
          <span :style="{ fontWeight: row.quantity < 10 ? 'bold' : 'normal' }">
            {{ row.quantity }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="更新时间" width="180" />
    </el-table>

    <div style="margin-top: 16px; text-align: right">
      <el-pagination
        v-model:current-page="page"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="onPageChange"
      />
    </div>

    <el-empty v-if="!loading && inventoryList.length === 0" description="暂无库存数据，请先完成入库操作" />
  </div>
</template>
