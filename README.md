# ApiDocument

ApiDocument 是一个 IntelliJ IDEA 插件，用于从 Java Spring MVC Controller 自动生成 ShowDoc 风格的 API Markdown 文档。

## 功能特性

- 在 Controller 类和接口方法旁显示行标记图标，点击即可生成接口文档。
- 自动解析接口 URL、请求方式、请求参数、返回参数和字段注释。
- 支持从 Git 分支名提取当前版本号。
- 支持排除父类字段、排除指定字段。
- 支持 Markdown 源码编辑和实时预览。
- 支持复制、导出、批量复制、批量导出。
- 支持中英文界面和中英文文档模板。
- 预览区支持主题、圆角代码块、圆角表格、JSON 格式化、复制代码块、复制表格、复制 URL。

## 开发环境

- JDK 17+
- IntelliJ IDEA 2023.2+
- Gradle Wrapper

## 构建

Windows:

```powershell
.\gradlew.bat buildPlugin
```

macOS / Linux:

```bash
./gradlew buildPlugin
```

构建后的插件包位于：

```text
build/distributions/apidocument-plugin-1.0.0.zip
```

## 安装

在 IntelliJ IDEA 中打开：

```text
Settings -> Plugins -> Install Plugin from Disk...
```

选择 `build/distributions/apidocument-plugin-1.0.0.zip` 安装即可。

## 使用方式

1. 打开一个 Spring MVC Controller Java 文件。
2. 点击类名或接口方法旁边的 ApiDocument 行标记图标。
3. 在弹出的工作台中查看、编辑、复制或导出生成的 Markdown 文档。
4. 如需调整作者、产品版本、语言、预览字号、排除父类字段等，点击工作台底部的"设置"。

## 主要配置

- 默认作者
- 产品版本
- 使用 Git 分支作为版本号
- 是否显示接口调用位置
- 是否显示请求参数 JSON
- 是否显示返回参数 JSON
- 是否显示 JSON 注释
- 界面语言
- 预览字号
- 导出路径
- 排除父类及字段

## 示例输出

```markdown
# 示例接口

|作者|创建时间|当前版本|产品版本|
|:----:|:----:|:----:|:----:|
|lilong|2026-06-30|V1.0.0|CMS 2.6.8|

**请求URL：**

- `/api/example/page`

**请求方式：**

- POST
- JSON

### 请求参数<业务参数>

|参数名|必选|类型|说明|
|:----|:---|:-----|-----|
|pageSize|否|Integer|每页大小|
```

## 技术栈

- Kotlin
- IntelliJ Platform SDK
- Gradle IntelliJ Plugin
- Gson
