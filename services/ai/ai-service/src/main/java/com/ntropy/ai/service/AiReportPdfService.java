package com.ntropy.ai.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ntropy.ai.exception.AiReportErrorCode;
import com.ntropy.ai.api.dto.AiReportDetailSummary;
import com.ntropy.common.exception.ServiceException;

/** AI 리포트 상세 표시 모델을 한글 텍스트·표 중심 PDF로 생성한다. */
@Service
public class AiReportPdfService {

    private static final String FONT_RESOURCE = "/fonts/NotoSansKR-VF.ttf";
    private static final DateTimeFormatter CREATED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int TABLE_TEXT_MAX_CODE_POINTS = 60;

    public byte[] generate(AiReportDetailSummary report) {
        try (PDDocument document = new PDDocument();
             InputStream fontStream = getClass().getResourceAsStream(FONT_RESOURCE)) {
            if (fontStream == null) {
                throw new IOException("PDF font resource is missing");
            }
            PDType0Font font = PDType0Font.load(document, fontStream);
            PdfWriter writer = new PdfWriter(document, font);
            writeReport(writer, report);
            writer.finish();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof ServiceException serviceException) {
                throw serviceException;
            }
            throw new ServiceException(AiReportErrorCode.PDF_GENERATION_FAILED, exception);
        }
    }

    private void writeReport(PdfWriter writer, AiReportDetailSummary report) throws IOException {
        JsonNode financial = objectOrEmpty(report.financialSummary());
        JsonNode recommendation = objectOrEmpty(report.recommendation());

        writer.reportHeader(report.yearMonth(), report.createdAt() == null ? "-" : CREATED_AT.format(report.createdAt()));
        writer.metricCards(
                won(financial.path("totalIncome")),
                won(financial.path("totalExpense")),
                won(financial.path("availableFunds"))
        );
        writer.insightCard(
                scalar(recommendation.path("financialActivityInsight")),
                "전월 대비 소득 " + percent(financial.path("incomeChangeRate"))
                        + " · 소비 " + percent(financial.path("expenseChangeRate"))
        );
        writer.cashFlowChart(financial.path("totalIncome"), financial.path("totalExpense"));
        writer.categoryChart(financial.path("topCategories"));

        writer.pageBreak();
        writer.pageTitle("잡별 근무 성과와 맞춤 추천");
        writer.jobChart(financial.path("jobSummaries"));
        writer.insightCard("잡별 소득 인사이트", scalar(recommendation.path("jobInsight")));
        writer.productCard(recommendation.path("recommendedProduct"));
        writer.paragraph("추천 이유", scalar(recommendation.path("reasoning")));
        String benefitLabel = benefitLabel(recommendation.path("recommendedProduct").path("productType"));
        writer.keyValue(benefitLabel, won(recommendation.path("simulatedExtraIncome")));
        writer.paragraph("향후 소득 전망", scalar(recommendation.path("futureIncomeTrend")));
        writer.keyValue("재무 유형", scalar(recommendation.path("financialType")));
        writeRecommendedProductDetails(writer, recommendation.path("recommendedProduct"));

        writeRemainingFields(writer, financial,
                Set.of("totalIncome", "totalExpense", "availableFunds", "incomeChangeRate",
                        "expenseChangeRate", "fixedExpense", "topCategories", "jobSummaries"));
        writeRemainingFields(writer, recommendation,
                Set.of("financialType", "financialActivityInsight", "jobInsight", "futureIncomeTrend",
                        "recommendedProduct", "reasoning", "simulatedExtraIncome"));

        writer.note("월 예상 혜택은 현재 재무 데이터와 상품 조건을 기준으로 계산한 단순 추정값이며, "
                + "실제 혜택은 납입 기간, 세금, 우대조건, 이용 실적 및 금융사 정책에 따라 달라질 수 있습니다.");
    }

    private void writeCategories(PdfWriter writer, JsonNode categories) throws IOException {
        writer.section("주요 소비 카테고리");
        List<List<String>> rows = new ArrayList<>();
        List<String> fullNames = new ArrayList<>();
        if (categories.isArray()) {
            for (JsonNode category : categories) {
                String displayName = scalar(category.path("displayName"));
                if (displayName.equals("-") && "AGGREGATED_OTHER".equals(category.path("category").asText())) {
                    displayName = "기타";
                }
                String tableName = abbreviateForTable(displayName);
                if (!tableName.equals(displayName)) {
                    fullNames.add(displayName);
                }
                rows.add(List.of(tableName, won(category.path("amount")), percent(category.path("ratio"))));
            }
        }
        writer.table(List.of("카테고리", "소비 금액", "소비 비율"), rows, new float[] {1.4f, 1, 0.8f});
        for (String fullName : fullNames) {
            writer.paragraph("전체 카테고리명", fullName);
        }
    }

    private void writeJobs(PdfWriter writer, JsonNode jobs) throws IOException {
        writer.section("잡별 소득 요약");
        List<List<String>> rows = new ArrayList<>();
        List<String> fullNames = new ArrayList<>();
        if (jobs.isArray()) {
            for (JsonNode job : jobs) {
                String jobName = scalar(job.path("jobName"));
                String tableName = abbreviateForTable(jobName);
                if (!tableName.equals(jobName)) {
                    fullNames.add(jobName);
                }
                rows.add(List.of(
                        tableName,
                        won(job.path("incomeAmount")),
                        percent(job.path("incomeRatio")),
                        workTime(job.path("totalWorkMinutes"))
                ));
            }
        }
        writer.table(List.of("잡 이름", "소득", "소득 비율", "근무시간"), rows,
                new float[] {1.2f, 1, 0.8f, 1});
        for (String fullName : fullNames) {
            writer.paragraph("전체 잡 이름", fullName);
        }
    }

    private void writeRecommendedProductDetails(PdfWriter writer, JsonNode product) throws IOException {
        if (product == null || !product.isObject() || product.size() == 0) {
            return;
        }
        writer.subsection("상품 상세 정보");
        writeDynamicNode(writer, product.path("details"), "상세");
        writeRemainingFields(writer, product,
                Set.of("productId", "productName", "provider", "productType", "summary", "targetGroup",
                        "njobTrendTip", "details"));
    }

    private void writeRemainingFields(PdfWriter writer, JsonNode object, Set<String> handled) throws IOException {
        if (!object.isObject()) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (!handled.contains(field.getKey())) {
                writeDynamicNode(writer, field.getValue(), humanize(field.getKey()));
            }
        }
    }

    private void writeDynamicNode(PdfWriter writer, JsonNode node, String label) throws IOException {
        if (node == null || node.isMissingNode() || node.isNull()) {
            writer.keyValue(label, "-");
        } else if (node.isObject()) {
            if (!"상세".equals(label)) {
                writer.subsection(label);
            }
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            if (!fields.hasNext()) {
                writer.keyValue(label, "-");
                return;
            }
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                writeDynamicNode(writer, field.getValue(), humanize(field.getKey()));
            }
        } else if (node.isArray()) {
            if (node.size() == 0) {
                writer.keyValue(label, "-");
            } else {
                writer.subsection(label);
                int index = 1;
                for (JsonNode child : node) {
                    writeDynamicNode(writer, child, "항목 " + index++);
                }
            }
        } else {
            writer.keyValue(label, scalar(node));
        }
    }

    private static JsonNode objectOrEmpty(JsonNode node) {
        return node == null ? com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode() : node;
    }

    private static String won(JsonNode node) {
        return node != null && node.isNumber()
                ? java.text.NumberFormat.getIntegerInstance(Locale.KOREA).format(node.asLong()) + "원"
                : "-";
    }

    private static String abbreviateForTable(String value) {
        if (value == null || value.codePointCount(0, value.length()) <= TABLE_TEXT_MAX_CODE_POINTS) {
            return value;
        }
        int end = value.offsetByCodePoints(0, TABLE_TEXT_MAX_CODE_POINTS);
        return value.substring(0, end) + "…";
    }

    private static String percent(JsonNode node) {
        if (node == null || !node.isNumber()) {
            return "-";
        }
        double value = node.asDouble() * 100;
        return String.format(Locale.KOREA, "%+.1f%%", value);
    }

    private static String unsignedPercent(JsonNode node) {
        if (node == null || !node.isNumber()) {
            return "-";
        }
        return String.format(Locale.KOREA, "%.1f%%", Math.max(0, node.asDouble()) * 100);
    }

    private static String workTime(JsonNode node) {
        if (node == null || !node.isNumber()) {
            return "-";
        }
        long minutes = Math.max(0, node.asLong());
        long hours = minutes / 60;
        long remainder = minutes % 60;
        if (hours == 0) return remainder + "분";
        return remainder == 0 ? hours + "시간" : hours + "시간 " + remainder + "분";
    }

    private static String benefitLabel(JsonNode productType) {
        String type = productType.asText("").toUpperCase(Locale.ROOT);
        if (type.contains("SAVING") || type.contains("DEPOSIT")) return "월 예상 이자";
        if (type.contains("CARD")) return "월 예상 절감액";
        return "월 예상 혜택";
    }

    private static String productTypeName(String type) {
        if (type == null || type.isBlank()) return "-";
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "CARD" -> "카드";
            case "SAVINGS", "SAVING", "DEPOSIT" -> "적금";
            default -> type;
        };
    }

    private static String scalar(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || node.asText().isBlank()) return "-";
        return node.isValueNode() ? node.asText() : node.toString();
    }

    private static String text(Object value) {
        return value == null ? "-" : value.toString();
    }

    private static String humanize(String key) {
        if (key == null || key.isBlank()) return "상세";
        String translated = switch (key) {
            case "interestRate" -> "금리";
            case "savingPeriod" -> "저축 기간";
            case "maxMonthlyAmount" -> "월 최대 납입액";
            case "minimumSpend" -> "최소 이용 금액";
            case "benefits" -> "혜택";
            case "rate" -> "비율";
            default -> null;
        };
        if (translated != null) return translated;
        String spaced = key.replace('_', ' ').replaceAll("([a-z0-9])([A-Z])", "$1 $2");
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    /** PDFBox의 저수준 API 위에 줄바꿈·표·페이지 넘김을 제공하는 작은 레이아웃 계층. */
    static final class PdfWriter {
        private static final float MARGIN = 48;
        private static final float BOTTOM = 48;
        private static final float BODY_SIZE = 10;
        private static final float LINE_HEIGHT = 15;
        private static final PDColor BRAND = rgb(0.02f, 0.47f, 0.35f);
        private static final PDColor BRAND_DARK = rgb(0.02f, 0.30f, 0.24f);
        private static final PDColor MINT = rgb(0.88f, 0.98f, 0.94f);
        private static final PDColor LIGHT = rgb(0.95f, 0.97f, 0.96f);
        private static final PDColor MUTED = rgb(0.42f, 0.47f, 0.45f);
        private static final PDColor[] CHART_COLORS = {
                rgb(0.02f, 0.47f, 0.35f),
                rgb(0.25f, 0.68f, 0.53f),
                rgb(0.52f, 0.82f, 0.69f),
                rgb(0.78f, 0.90f, 0.84f)
        };

        private final PDDocument document;
        private final PDType0Font font;
        private PDPage page;
        private PDPageContentStream stream;
        private float y;
        private int pageNumber;

        PdfWriter(PDDocument document, PDType0Font font) throws IOException {
            this.document = document;
            this.font = font;
            newPage();
        }

        void reportHeader(String yearMonth, String createdAt) throws IOException {
            ensure(70);
            text("Ntropy", MARGIN, y, 12, BRAND);
            text(monthTitle(yearMonth), MARGIN, y - 26, 22, BRAND_DARK);
            text("AI 월간 재무 리포트 · 생성 " + createdAt, MARGIN, y - 46, 8, MUTED);
            y -= 66;
        }

        void pageTitle(String value) throws IOException {
            ensure(52);
            text("Ntropy AI 월간 재무 리포트", MARGIN, y, 9, BRAND);
            text(value, MARGIN, y - 25, 18, BRAND_DARK);
            y -= 48;
        }

        void metricCards(String income, String expense, String available) throws IOException {
            ensure(76);
            float gap = 10;
            float width = (contentWidth() - gap * 2) / 3;
            metricCard(MARGIN, y, width, "총소득", income, LIGHT, BRAND_DARK);
            metricCard(MARGIN + width + gap, y, width, "총소비", expense, LIGHT, BRAND_DARK);
            metricCard(MARGIN + (width + gap) * 2, y, width, "가용자금", available, MINT, BRAND);
            y -= 82;
        }

        void insightCard(String headline, String detail) throws IOException {
            List<String> headlineLines = wrap(headline, contentWidth() - 28, 11);
            List<String> detailLines = wrap(detail, contentWidth() - 28, 8);
            float height = 48 + headlineLines.size() * 15 + detailLines.size() * 12;
            if (height > 180) {
                paragraph("AI 맞춤 제안", headline);
                paragraph("상세", detail);
                return;
            }
            ensure(height + 8);
            fillRect(MARGIN, y - height, contentWidth(), height, MINT);
            text("AI 맞춤 제안", MARGIN + 14, y - 18, 8, BRAND);
            float lineY = y - 38;
            for (String line : headlineLines) {
                text(line, MARGIN + 14, lineY, 11, BRAND_DARK);
                lineY -= 15;
            }
            for (String line : detailLines) {
                text(line, MARGIN + 14, lineY - 2, 8, MUTED);
                lineY -= 12;
            }
            y -= height + 18;
        }

        void cashFlowChart(JsonNode incomeNode, JsonNode expenseNode) throws IOException {
            ensure(160);
            text("이번 달 자금 흐름", MARGIN, y, 13, BRAND_DARK);
            long income = nonNegativeLong(incomeNode);
            long expense = nonNegativeLong(expenseNode);
            double ratio = income == 0 ? 0 : Math.min(1d, (double) expense / income);

            float centerX = MARGIN + 72;
            float centerY = y - 82;
            drawRing(centerX, centerY, 42, 11, rgb(0.86f, 0.91f, 0.89f), 1d);
            drawRing(centerX, centerY, 42, 11, BRAND, ratio);
            String ratioText = Math.round(ratio * 100) + "%";
            textCentered(ratioText, centerX, centerY + 3, 14, BRAND_DARK);
            textCentered("소비율", centerX, centerY - 13, 8, MUTED);

            float labelX = MARGIN + 155;
            text("총소득", labelX, y - 57, 9, MUTED);
            text(won(incomeNode), labelX + 78, y - 57, 10, BRAND_DARK);
            text("총소비", labelX, y - 89, 9, MUTED);
            text(won(expenseNode), labelX + 78, y - 89, 10, BRAND_DARK);
            text("남은 가용자금", labelX, y - 121, 9, MUTED);
            text(java.text.NumberFormat.getIntegerInstance(Locale.KOREA)
                    .format(income - expense) + "원", labelX + 78, y - 121, 10, BRAND);
            y -= 155;
        }

        void categoryChart(JsonNode categories) throws IOException {
            text("주요 소비 카테고리", MARGIN, y, 13, BRAND_DARK);
            List<JsonNode> items = firstItems(categories, 4);
            if (items.isEmpty()) {
                y -= 20;
                paragraph("카테고리", "데이터 없음");
                return;
            }
            ensure(52 + items.size() * 24);
            double totalRatio = items.stream().mapToDouble(PdfWriter::ratio).sum();
            float barY = y - 28;
            float barX = MARGIN;
            float barWidth = contentWidth();
            for (int index = 0; index < items.size(); index++) {
                double normalized = totalRatio <= 0 ? 1d / items.size() : ratio(items.get(index)) / totalRatio;
                float segmentWidth = (float) (barWidth * normalized);
                fillRect(barX, barY, segmentWidth, 12, CHART_COLORS[index]);
                barX += segmentWidth;
            }
            float rowY = barY - 19;
            for (int index = 0; index < items.size(); index++) {
                JsonNode item = items.get(index);
                String name = categoryName(item);
                fillRect(MARGIN, rowY - 6, 7, 7, CHART_COLORS[index]);
                text(abbreviate(name, 30), MARGIN + 14, rowY, 9, null);
                text(won(item.path("amount")), MARGIN + 250, rowY, 9, BRAND_DARK);
                text(unsignedPercent(item.path("ratio")), MARGIN + 390, rowY, 9, MUTED);
                rowY -= 22;
                if (!abbreviate(name, 30).equals(name)) {
                    paragraph("전체 카테고리명", name);
                    rowY = y;
                }
            }
            y = Math.min(y, rowY - 5);
        }

        void jobChart(JsonNode jobs) throws IOException {
            text("잡별 근무 분석", MARGIN, y, 13, BRAND_DARK);
            y -= 24;
            List<JsonNode> items = firstItems(jobs, 6);
            if (items.isEmpty()) {
                y -= 20;
                paragraph("잡별 성과", "데이터 없음");
                return;
            }
            long maxIncome = items.stream().mapToLong(item -> nonNegativeLong(item.path("incomeAmount"))).max().orElse(0);
            for (JsonNode item : items) {
                ensure(48);
                String name = scalar(item.path("jobName"));
                String shortName = abbreviate(name, 26);
                text(shortName, MARGIN, y - 8, 10, BRAND_DARK);
                text(workTime(item.path("totalWorkMinutes")) + " · " + won(item.path("incomeAmount")),
                        MARGIN + 250, y - 8, 9, MUTED);
                fillRect(MARGIN, y - 29, contentWidth(), 9, LIGHT);
                float width = maxIncome == 0 ? 0 : contentWidth() * nonNegativeLong(item.path("incomeAmount")) / maxIncome;
                fillRect(MARGIN, y - 29, width, 9, BRAND);
                y -= 43;
                if (!shortName.equals(name)) paragraph("전체 잡 이름", name);
            }
            y -= 5;
        }

        void productCard(JsonNode product) throws IOException {
            if (product == null || !product.isObject() || product.size() == 0) {
                insightCard("추천 상품이 아직 없어요.", "재무 데이터가 쌓이면 맞춤 상품을 안내해 드릴게요.");
                return;
            }
            String provider = scalar(product.path("provider"));
            String productName = scalar(product.path("productName"));
            String summary = scalar(product.path("summary"));
            List<String> nameLines = wrap(productName, contentWidth() - 145, 13);
            List<String> summaryLines = wrap(summary, contentWidth() - 28, 9);
            float height = 68 + nameLines.size() * 17 + summaryLines.size() * 13;
            ensure(height + 8);
            fillRect(MARGIN, y - height, contentWidth(), height, LIGHT);
            text(provider, MARGIN + 14, y - 20, 9, BRAND);
            badge("맞춤 추천", MARGIN + contentWidth() - 82, y - 25, 68);
            float lineY = y - 45;
            for (String line : nameLines) {
                text(line, MARGIN + 14, lineY, 13, BRAND_DARK);
                lineY -= 17;
            }
            for (String line : summaryLines) {
                text(line, MARGIN + 14, lineY - 3, 9, MUTED);
                lineY -= 13;
            }
            y -= height + 18;
        }

        void pageBreak() throws IOException {
            newPage();
        }

        void title(String value) throws IOException {
            ensure(42);
            text(value, MARGIN, y, 20, BRAND);
            y -= 34;
        }

        void section(String value) throws IOException {
            ensure(38);
            y -= 8;
            stream.setNonStrokingColor(BRAND);
            stream.addRect(MARGIN, y - 20, page.getMediaBox().getWidth() - MARGIN * 2, 25);
            stream.fill();
            text(value, MARGIN + 8, y - 13, 13, new PDColor(new float[] {1, 1, 1}, PDDeviceRGB.INSTANCE));
            y -= 32;
        }

        void subsection(String value) throws IOException {
            ensure(28);
            y -= 5;
            text(value, MARGIN, y, 11, BRAND);
            y -= 19;
        }

        void keyValue(String key, String value) throws IOException {
            paragraph(key, value);
        }

        void paragraph(String label, String value) throws IOException {
            ensure(LINE_HEIGHT * 2);
            text(label, MARGIN, y, 10, BRAND);
            y -= LINE_HEIGHT;
            for (String line : wrap(value, page.getMediaBox().getWidth() - MARGIN * 2, BODY_SIZE)) {
                ensure(LINE_HEIGHT);
                text(line, MARGIN, y, BODY_SIZE, null);
                y -= LINE_HEIGHT;
            }
            y -= 4;
        }

        void note(String value) throws IOException {
            ensure(35);
            y -= 8;
            for (String line : wrap("※ " + value, page.getMediaBox().getWidth() - MARGIN * 2, 8)) {
                ensure(12);
                text(line, MARGIN, y, 8, null);
                y -= 12;
            }
        }

        void table(List<String> headers, List<List<String>> rows, float[] weights) throws IOException {
            float width = page.getMediaBox().getWidth() - MARGIN * 2;
            float totalWeight = 0;
            for (float weight : weights) totalWeight += weight;
            float[] columnWidths = new float[weights.length];
            for (int i = 0; i < weights.length; i++) columnWidths[i] = width * weights[i] / totalWeight;

            tableRow(headers, columnWidths, true);
            if (rows.isEmpty()) {
                tableRow(List.of("데이터 없음"), new float[] {width}, false);
            } else {
                for (List<String> row : rows) tableRow(row, columnWidths, false);
            }
            y -= 8;
        }

        private void tableRow(List<String> cells, float[] widths, boolean header) throws IOException {
            List<List<String>> wrapped = new ArrayList<>();
            int maxLines = 1;
            for (int i = 0; i < cells.size(); i++) {
                float cellWidth = widths[Math.min(i, widths.length - 1)] - 10;
                List<String> lines = wrap(cells.get(i), cellWidth, 9);
                wrapped.add(lines);
                maxLines = Math.max(maxLines, lines.size());
            }
            float height = maxLines * 13 + 10;
            ensure(height);

            float x = MARGIN;
            for (int i = 0; i < wrapped.size(); i++) {
                float cellWidth = widths[Math.min(i, widths.length - 1)];
                if (header) {
                    stream.setNonStrokingColor(LIGHT);
                    stream.addRect(x, y - height + 4, cellWidth, height);
                    stream.fill();
                }
                stream.setStrokingColor(0.71f, 0.75f, 0.80f);
                stream.addRect(x, y - height + 4, cellWidth, height);
                stream.stroke();
                float lineY = y - 10;
                for (String line : wrapped.get(i)) {
                    text(line, x + 5, lineY, 9, header ? BRAND : null);
                    lineY -= 13;
                }
                x += cellWidth;
            }
            y -= height;
        }

        private List<String> wrap(String raw, float maxWidth, float size) throws IOException {
            String value = raw == null || raw.isBlank() ? "-" : raw.replace('\n', ' ').replace('\r', ' ');
            List<String> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (int offset = 0; offset < value.length();) {
                int codePoint = value.codePointAt(offset);
                String character = new String(Character.toChars(codePoint));
                String candidate = line + character;
                if (line.length() > 0 && stringWidth(candidate, size) > maxWidth) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                line.append(character);
                offset += Character.charCount(codePoint);
            }
            if (line.length() > 0) lines.add(line.toString());
            if (lines.isEmpty()) lines.add("-");
            return lines;
        }

        private float stringWidth(String value, float size) throws IOException {
            return font.getStringWidth(value) / 1000f * size;
        }

        private void text(String value, float x, float baseline, float size, PDColor color) throws IOException {
            stream.beginText();
            stream.setFont(font, size);
            stream.setNonStrokingColor(color == null
                    ? new PDColor(new float[] {0.12f, 0.14f, 0.18f}, PDDeviceRGB.INSTANCE)
                    : color);
            stream.newLineAtOffset(x, baseline);
            stream.showText(value == null ? "-" : value);
            stream.endText();
        }

        private void metricCard(float x, float top, float width, String label, String value,
                                PDColor background, PDColor valueColor) throws IOException {
            fillRect(x, top - 66, width, 66, background);
            text(label, x + 12, top - 20, 9, MUTED);
            text(abbreviate(value, 18), x + 12, top - 45, 13, valueColor);
        }

        private void badge(String value, float x, float baseline, float width) throws IOException {
            fillRect(x, baseline - 4, width, 19, MINT);
            float textX = x + (width - stringWidth(value, 8)) / 2;
            text(value, textX, baseline + 2, 8, BRAND);
        }

        private void fillRect(float x, float bottom, float width, float height, PDColor color) throws IOException {
            stream.setNonStrokingColor(color);
            stream.addRect(x, bottom, Math.max(0, width), Math.max(0, height));
            stream.fill();
        }

        private void drawRing(float centerX, float centerY, float radius, float lineWidth,
                              PDColor color, double portion) throws IOException {
            double clamped = Math.max(0, Math.min(1, portion));
            if (clamped == 0) return;
            int segments = Math.max(2, (int) Math.ceil(120 * clamped));
            stream.setStrokingColor(color);
            stream.setLineWidth(lineWidth);
            stream.setLineCapStyle(1);
            for (int index = 0; index <= segments; index++) {
                double angle = -Math.PI / 2 + Math.PI * 2 * clamped * index / segments;
                float x = centerX + (float) Math.cos(angle) * radius;
                float pointY = centerY + (float) Math.sin(angle) * radius;
                if (index == 0) stream.moveTo(x, pointY);
                else stream.lineTo(x, pointY);
            }
            stream.stroke();
            stream.setLineCapStyle(0);
        }

        private void textCentered(String value, float centerX, float baseline, float size, PDColor color)
                throws IOException {
            text(value, centerX - stringWidth(value, size) / 2, baseline, size, color);
        }

        private float contentWidth() {
            return page.getMediaBox().getWidth() - MARGIN * 2;
        }

        private void ensure(float required) throws IOException {
            if (y - required < BOTTOM) newPage();
        }

        private void newPage() throws IOException {
            if (stream != null) {
                addPageNumber();
                stream.close();
            }
            page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = page.getMediaBox().getHeight() - MARGIN;
            pageNumber++;
        }

        private void addPageNumber() throws IOException {
            String value = "- " + pageNumber + " -";
            float x = (page.getMediaBox().getWidth() - stringWidth(value, 8)) / 2;
            text(value, x, 24, 8, null);
        }

        void finish() throws IOException {
            addPageNumber();
            stream.close();
            stream = null;
        }

        private static String monthTitle(String yearMonth) {
            if (yearMonth == null || !yearMonth.matches("\\d{4}-\\d{2}")) {
                return "AI 월간 리포트";
            }
            return yearMonth.substring(0, 4) + "년 "
                    + Integer.parseInt(yearMonth.substring(5, 7)) + "월";
        }

        private static long nonNegativeLong(JsonNode node) {
            return node != null && node.isNumber() ? Math.max(0, node.asLong()) : 0;
        }

        private static double ratio(JsonNode item) {
            JsonNode node = item.path("ratio");
            if (!node.isNumber()) return 0;
            return Math.max(0, node.asDouble());
        }

        private static List<JsonNode> firstItems(JsonNode array, int limit) {
            List<JsonNode> result = new ArrayList<>();
            if (array == null || !array.isArray()) return result;
            for (JsonNode item : array) {
                if (result.size() == limit) break;
                result.add(item);
            }
            return result;
        }

        private static String categoryName(JsonNode category) {
            String displayName = scalar(category.path("displayName"));
            if ("-".equals(displayName)) displayName = scalar(category.path("category"));
            return "AGGREGATED_OTHER".equals(displayName) ? "기타" : displayName;
        }

        private static String abbreviate(String value, int maxCodePoints) {
            if (value == null || value.codePointCount(0, value.length()) <= maxCodePoints) return value;
            int end = value.offsetByCodePoints(0, maxCodePoints);
            return value.substring(0, end) + "…";
        }

        private static PDColor rgb(float red, float green, float blue) {
            return new PDColor(new float[] {red, green, blue}, PDDeviceRGB.INSTANCE);
        }
    }
}
