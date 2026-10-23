# 新侨课程表 Android 应用

Material 3（Android 16 现代风格）课程表应用，底部导航包含「概括」「课表」「配置」三个页面，支持多套课程表配置、周一至周日完整周表、自定义加课。

## 功能

### 概括页面（自动刷新，粗体大字）
- **现在是什么课**：课程名 + 第几节 + 时间段 + 本节课进度条，自动切换「上课中 / 自习时段 / 课间休息 / 课前等待 / 已放学 / 周末休息」
- **还有几分钟下课**（可精确到秒）
- **下一节课**：课程名、星期/节次/时间、距上课倒计时（自动跳过“无”课节与无课日）
- **距离放学**（到当天最后一节结束）、**周末倒计时**（到周五最后一节结束；休息日自动变为**返校倒计时**到下一个上学日第一节）
- 今日课程进度、实时时钟、日期星期、当前配置名

### 课表页面
- 周一至周日七天切换，默认选中当天；无课日（如周六）显示空提示
- 当前正在上的课主题色高亮 +「进行中」，已结束/未开始分别标注，自动滚动定位

### 配置页面
**外观与显示**
- 深色模式：跟随系统 / 浅色 / 深色
- 主题配色：动态取色（Material You 跟随壁纸，安卓 12+）/ 青绿 / 海洋蓝 / 紫罗兰 / 活力橙 / 樱花粉，浅深色各有完整配色
- 深色纯黑模式：深色模式下把灰色背景压成纯黑（AMOLED，更省电）
- 倒计时精确到秒：关闭后倒计时按分钟（向上取整）显示

**默认配置（内置，只读）**
- 独立板块展示 **新侨中学高二一班**（周一至周五课程 + 周日 4 节晚自习，周六无课）
- 单击仅切换为当前使用，不进入编辑；不可重命名、不可删除、不可改课；想改动请在下方复制一份

**自定义配置（可编辑）**
- 独立板块管理，支持 **空白新建** 或一键 **复制默认配置**，也可对已有自定义配置复制一份
- **单击配置行只切换为当前使用**；编辑课程、重命名、复制、删除统一走右侧「三点」菜单
- 编辑器支持周一至周日任选一天：
  - 点任意一节课修改名称（留空记为“无”）
  - **添加一节课**（第 13、14 节……）：可设置课程名与起止时间
  - 自己新增的课程可再次修改名称/时间，或删除（内置节次不可删）
- 所有配置与设置保存在本机 SharedPreferences，无需联网；旧版本数据自动迁移（旧的可编辑默认表回收为只读内置表，自定义配置保留，并补全周六、周日晚自习）

## 技术栈
- 语言：Java 17，minSdk 26（Android 8.0），targetSdk/compileSdk 34
- Material Components 1.12（Material 3 / Material You，Android 12+ 动态取色）
- AndroidX Fragment / RecyclerView / ViewBinding；java.time 时间计算
- 单 Activity + Fragment（概括 / 课表 / 配置 / 课程编辑器）+ BottomNavigationView

## 目录结构
```
app/src/main/java/com/classschedule/
├── MainActivity.java                 # 宿主 Activity + 底部导航 + 编辑器跳转
├── data/
│   ├── PeriodItem.java               # 单节数据（名称/起止时间/是否自定义）
│   ├── DayPlan.java                  # 一天的节次列表（节数可变）
│   ├── CoursePeriod.java             # 运行时一节课
│   ├── DaySchedule.java              # 一天课表（空安全/首末节查找）
│   ├── WeekTable.java                # 由配置构建的一周课表（7 天）
│   ├── ScheduleProfile.java          # 一套课程表配置（JSON 序列化 + 旧版迁移）
│   ├── SettingsStore.java            # 多配置与设置的持久化中心（单例）
│   ├── ScheduleRepository.java       # 默认时间段与内置课程表数据
│   └── TimeFmt.java                  # 中文时间格式化
├── engine/
│   ├── ScheduleEngine.java           # 当前课/下节课/倒计时推导
│   └── ScheduleStatus.java           # 状态快照
└── ui/
    ├── OverviewFragment.java         # 概括页
    ├── ScheduleFragment.java         # 课表页
    ├── SettingsFragment.java         # 配置页
    ├── ProfileEditFragment.java      # 课程编辑器（改名/加课/改时间/删除）
    └── PeriodAdapter.java            # 课表列表适配器
```

## 修改内置默认课程表
编辑 `data/ScheduleRepository.java`：
- `STARTS/ENDS`：12 个统一时间段
- `DEFAULT_WEEK`：5 行（周一至周五）× 12 节的默认课程名
- `SUNDAY_EVENING`：周日晚自习
- `DEFAULT_PROFILE_NAME`：默认配置名称

应用运行后也可以直接在「配置」页新建/编辑，无需改代码。

## 构建
```bash
# 需要 JDK 17
./gradlew assembleRelease     # 产物：app/build/outputs/apk/release/app-release.apk
./gradlew assembleDebug       # 调试包
```

### 签名说明
release 使用 `app/release.keystore`（仅用于侧载安装）：
- alias：`classschedule`
- 密钥库/密钥口令：`classschedule2026`

正式发布请替换为自己的密钥库并修改 `app/build.gradle` 的 `signingConfigs.release`。

## 安装
将 `app-release.apk` 传到安卓手机，允许「安装未知来源应用」后点击安装即可。
