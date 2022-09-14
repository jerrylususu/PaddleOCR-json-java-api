# PaddleOCR-json-java-api

A thin wrapper for [PaddleOCR-json](https://github.com/hiroi-sora/PaddleOCR-json).

## Requires
- Gson

## Usage
1. Include 'PaddleOcrJson.java' in your project
2. Invoke OCR as follows: (See [Main.java](https://github.com/jerrylususu/PaddleOCR-json-java-api/blob/main/src/main/java/org/example/Main.java))

```java
// paddleocr_json 的可执行文件所在路径
String exePath = "path/to/executable";

// 可选的配置项
Map<String, Object> arguments = new HashMap<>();
// arguments.put("use_angle_cls", true);

// 初始化 OCR
try (Ocr ocr = new Ocr(new File(exePath), arguments)) {

    // 对一张图片进行 OCR
    String imgPath = "path/to/img";
    OcrResponse resp = ocr.runOcr(new File(imgPath));

    // 读取结果
    if (resp.code == OcrCode.OK) {
        for (OcrEntry entry : resp.data) {
            System.out.println(entry.text);
        }
    } else {
        System.out.println("error: code=" + resp.code + " msg=" + resp.msg);
    }
} catch (IOException e) {
    e.printStackTrace();
}
```

## Reference
- Unicode to ASCII escape: [EscapedWriter from Soot Project](https://github.com/soot-oss/soot/blob/3966f565db6dc2882c3538ffc39e44f4c14b5bcf/src/main/java/soot/util/EscapedWriter.java)
