import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import InventoryView from '@/views/InventoryView.vue'
import api from '@/api/client'

// Mock axios 客户端，拦截所有 API 调用
vi.mock('@/api/client', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
  },
}))

const mockInventoryResponse = {
  code: 200,
  message: 'success',
  data: {
    list: [
      { productId: 1, productName: '蓝牙耳机 Pro', sku: 'SKU-001', locationCode: 'WH-A-01-01', warehouseName: '广州主仓', quantity: 150, updatedAt: '2026-05-22T10:00:00' },
      { productId: 2, productName: '无线充电板', sku: 'SKU-003', locationCode: 'WH-A-01-02', warehouseName: '广州主仓', quantity: 5, updatedAt: '2026-05-22T10:00:00' },
      { productId: 3, productName: 'Type-C数据线', sku: 'SKU-002', locationCode: 'WH-B-01-01', warehouseName: '深圳保税仓', quantity: 80, updatedAt: '2026-05-22T10:00:00' },
    ],
    total: 3,
    page: 1,
    pageSize: 20,
  },
}

const mockWarehousesResponse = {
  code: 200,
  message: 'success',
  data: [
    { id: 1, code: 'WH-A', name: '广州主仓' },
    { id: 2, code: 'WH-B', name: '深圳保税仓' },
  ],
}

function setupMocks() {
  const mockGet = vi.mocked(api.get)
  mockGet.mockImplementation((url: string, config?: any) => {
    if (url === '/warehouses') {
      return Promise.resolve(mockWarehousesResponse)
    }
    if (url === '/inventory') {
      return Promise.resolve(mockInventoryResponse)
    }
    return Promise.resolve({ code: 200, data: null })
  })
  return mockGet
}

// ==================== 关键词搜索 ====================
describe('库存列表筛选 - 关键词搜索', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // 测试：页面初始加载时keyword为空，应查询全部库存数据
  it('初始加载时keyword为空，查询全部库存', async () => {
    const mockGet = setupMocks()
    mount(InventoryView, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    const inventoryCalls = mockGet.mock.calls.filter(c => c[0] === '/inventory')
    expect(inventoryCalls.length).toBeGreaterThanOrEqual(1)
    const params = inventoryCalls[0][1]?.params
    expect(params?.keyword).toBeFalsy()
  })

  // 测试：输入关键词后防抖400ms触发查询，请求参数携带keyword
  it('输入关键词后防抖400ms触发查询，请求携带keyword参数', async () => {
    const mockGet = setupMocks()
    const wrapper = mount(InventoryView, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    const vm = wrapper.vm as any
    vm.keyword = '蓝牙'
    vm.onKeywordInput()

    // 防抖 400ms 前不应有新请求
    const callsBeforeDebounce = mockGet.mock.calls.filter(c => c[0] === '/inventory').length
    await new Promise(r => setTimeout(r, 500))
    await flushPromises()

    const callsAfterDebounce = mockGet.mock.calls.filter(c => c[0] === '/inventory').length
    expect(callsAfterDebounce).toBeGreaterThan(callsBeforeDebounce)

    // 最后一次 inventory 请求应携带 keyword
    const lastInventoryCall = mockGet.mock.calls.filter(c => c[0] === '/inventory').pop()
    expect(lastInventoryCall[1]?.params?.keyword).toBe('蓝牙')
  })
})

// ==================== 仓库筛选 ====================
describe('库存列表筛选 - 仓库筛选', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // 测试：选择仓库后API请求携带warehouseId参数，页码重置为第1页
  it('选择仓库后请求携带warehouseId参数，且页码重置为1', async () => {
    const mockGet = setupMocks()
    const wrapper = mount(InventoryView, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    const vm = wrapper.vm as any
    vm.warehouseId = 1
    vm.page = 1
    vm.loadInventory()
    await flushPromises()

    const lastCall = mockGet.mock.calls.filter(c => c[0] === '/inventory').pop()
    expect(lastCall[1]?.params?.warehouseId).toBe(1)
    expect(lastCall[1]?.params?.page).toBe(1)
  })

  // 测试：清空仓库筛选后warehouseId为undefined，恢复查询全部库存
  it('清空仓库筛选后warehouseId为undefined，查询全部', async () => {
    const mockGet = setupMocks()
    const wrapper = mount(InventoryView, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    const vm = wrapper.vm as any
    vm.warehouseId = undefined
    vm.loadInventory()
    await flushPromises()

    const lastCall = mockGet.mock.calls.filter(c => c[0] === '/inventory').pop()
    expect(lastCall[1]?.params?.warehouseId).toBeUndefined()
  })
})

// ==================== 低库存高亮 ====================
describe('库存列表筛选 - 低库存高亮', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // 测试：库存数量<10的行显示红色高亮样式，>=10的行无高亮
  it('库存数量<10的行返回红色样式，>=10的行返回空样式', async () => {
    setupMocks()
    const wrapper = mount(InventoryView, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    const vm = wrapper.vm as any

    // quantity < 10 → 红色高亮
    const lowStyle = vm.getRowStyle({ row: { quantity: 5 } })
    expect(lowStyle).toEqual({ backgroundColor: '#fff0f0', color: '#f56c6c' })

    // quantity = 9 → 红色高亮（边界值）
    const edgeLowStyle = vm.getRowStyle({ row: { quantity: 9 } })
    expect(edgeLowStyle).toEqual({ backgroundColor: '#fff0f0', color: '#f56c6c' })

    // quantity = 10 → 无高亮
    const normalStyle = vm.getRowStyle({ row: { quantity: 10 } })
    expect(normalStyle).toEqual({})

    // quantity = 150 → 无高亮
    const highStyle = vm.getRowStyle({ row: { quantity: 150 } })
    expect(highStyle).toEqual({})
  })
})

// ==================== 分页 ====================
describe('库存列表筛选 - 分页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // 测试：翻页时API请求携带正确的page参数
  it('翻页时请求携带正确的page参数', async () => {
    const mockGet = setupMocks()
    const wrapper = mount(InventoryView, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    const vm = wrapper.vm as any
    vm.onPageChange(3)
    await flushPromises()

    const lastCall = mockGet.mock.calls.filter(c => c[0] === '/inventory').pop()
    expect(lastCall[1]?.params?.page).toBe(3)
  })

  // 测试：切换每页条数时页码重置为1，请求携带新的pageSize
  it('切换pageSize时页码重置为1，请求携带新的pageSize', async () => {
    const mockGet = setupMocks()
    const wrapper = mount(InventoryView, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    const vm = wrapper.vm as any
    vm.onSizeChange(50)
    await flushPromises()

    const lastCall = mockGet.mock.calls.filter(c => c[0] === '/inventory').pop()
    expect(lastCall[1]?.params?.pageSize).toBe(50)
    expect(lastCall[1]?.params?.page).toBe(1)
  })
})