# 统一数据交换格式

当前格式版本为 `1`。Android、Web 和 iOS 均导入、导出同一结构：

```json
{
  "format": "health-assistant",
  "version": 1,
  "exportedAt": "2026-06-19T08:00:00Z",
  "records": [
    {
      "id": "1770422949263",
      "value": 7.9,
      "measuredAt": "2026-02-07T08:09:00+08:00",
      "mealType": "空腹",
      "notes": ""
    }
  ]
}
```

兼容旧版 `kv-export.json`：文件顶层为对象，键为 `record_<id>`，值为 JSON 字符串。导入器会读取其中的 `id`、`value`、`date`、`time`、`mealType`、`notes` 和 `datetime`。

合并规则：

1. 以 `id` 唯一标识记录。
2. 同一 `id` 以 `updatedAt` 较新的版本为准；旧格式缺少该字段时使用测量时间。
3. WebDAV 同步执行“下载 → 合并 → 上传”，不会因为单端缺少记录而直接删除远端记录。
4. 删除采用墓碑记录，保留 90 天，避免离线设备同步后恢复已删除数据。

