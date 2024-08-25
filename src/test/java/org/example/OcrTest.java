package org.example;

import org.junit.Test;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class OcrTest {

    private static final String exePath = System.getenv().getOrDefault("PADDLEOCR_JSON_EXE_PATH", "C:\\Temporary\\paddleocr\\PaddleOCR-json_v1.4.0\\PaddleOCR-json.exe");
    private static final String imgPath = System.getenv().getOrDefault("PADDLEOCR_JSON_TEST_IMG_PATH", "C:\\Projects\\PaddleOCR-json-java-api\\res\\test.png");
    private static final String kTestContent = "helloworld";
    private static final int kTestPort = Integer.parseInt(System.getenv().getOrDefault("PADDLEOCR_JSON_TEST_PORT", "23333"));

    @Test
    public void TestLocalMode() {
        System.out.println("using exePath: " + exePath);

        Map<String, Object> arguments = new HashMap<>();

        OcrResponse resp = null;
        try (Ocr ocr = new Ocr(new File(exePath), arguments)) {

            resp = ocr.runOcr(new File(imgPath));

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

        assertEquals(resp.code, OcrCode.OK);
        assertEquals(resp.data.length, 1);
        assertEquals(resp.data[0].text, kTestContent);
    }

    @Test
    public void TestSocketMode() {
        System.out.println("using exePath: " + exePath);

        Map<String, Object> serverArguments = new HashMap<>();
        serverArguments.put("port", kTestPort);
        OcrResponse resp = null;

        // 在外层启动一个监听 kTestPort 的 paddleocr-json 服务
        try (Ocr ocrServer = new Ocr(new File(exePath), serverArguments)) {
            
            // 等开始监听
            System.out.println("sleep 5s to make sure the server is listening");
            Thread.sleep(5000);

            Map<String, Object> arugments = new HashMap<>();
            try (Ocr ocr = new Ocr("localhost", kTestPort, arugments)) {

                byte[] fileBytes = Files.readAllBytes(Paths.get(imgPath));
                resp = ocr.runOcrOnImgBytes(fileBytes);

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
        } catch (IOException e) {
            e.printStackTrace();
        }

        assertEquals(resp.code, OcrCode.OK);
        assertEquals(resp.data.length, 1);
        assertEquals(resp.data[0].text, kTestContent);
    }
}
