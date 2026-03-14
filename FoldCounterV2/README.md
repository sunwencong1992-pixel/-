# 折叠计数器 FoldCounter

## 如何通过 GitHub Actions 自动编译 APK

### 第一步：注册 / 登录 GitHub
访问 https://github.com，注册或登录账号。

### 第二步：创建新仓库
1. 点击右上角 **+** → **New repository**
2. Repository name 填写：`FoldCounter`
3. 选择 **Public**（公开，免费使用 Actions）
4. 点击 **Create repository**

### 第三步：上传项目文件
在新建仓库页面，点击 **uploading an existing file**，
将本压缩包解压后，把 **FoldCounter 文件夹内的所有文件** 全部拖进去，
然后点击底部 **Commit changes**。

### 第四步：等待自动编译
上传完成后，点击仓库顶部 **Actions** 标签页，
你会看到一个名为 **Build APK** 的任务正在运行（黄色圆圈）。
等待 2～5 分钟，变成绿色 ✅ 即编译成功。

### 第五步：下载 APK
1. 点击那条绿色的 **Build APK** 记录
2. 页面底部 **Artifacts** 区域
3. 点击 **FoldCounter-debug** 下载 zip
4. 解压得到 `app-debug.apk`

### 第六步：安装到手机
1. 手机开启「允许安装未知来源应用」
   - 设置 → 安全 → 安装未知应用
2. 将 APK 传到手机（微信、QQ、数据线均可）
3. 点击 APK 文件安装即可

---

## 注意事项
- 需要**折叠屏手机**，普通手机无铰链传感器
- 系统要求 **Android 11 及以上**
- 每次合上屏幕计为一次翻折，数据持久保存
