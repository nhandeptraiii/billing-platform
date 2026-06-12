-- ============================================================
-- Script import danh sách Cụm (Region) vào bảng regions
-- Nếu tên đã tồn tại thì bỏ qua (INSERT ... WHERE NOT EXISTS)
-- Chạy trên MySQL: source import_regions.sql
-- ============================================================

SET NAMES utf8mb4;

INSERT INTO regions (name, created_at, updated_at)
SELECT t.name, NOW(), NOW()
FROM (
    SELECT 'Cụm Bình Thủy'    AS name UNION ALL
    SELECT 'Cụm Cái Răng'              UNION ALL
    SELECT 'Cụm Cờ Đỏ'                UNION ALL
    SELECT 'Cụm Ninh Kiều'            UNION ALL
    SELECT 'Cụm Ô Môn'                UNION ALL
    SELECT 'Cụm Phong Điền'           UNION ALL
    SELECT 'Cụm Thới Lai'             UNION ALL
    SELECT 'Cụm Thốt Nốt'             UNION ALL
    SELECT 'Cụm Vĩnh Thạnh'           UNION ALL
    SELECT 'Cụm Châu Thành 1'         UNION ALL
    SELECT 'Cụm Châu Thành A'         UNION ALL
    SELECT 'Cụm Long Mỹ'              UNION ALL
    SELECT 'Cụm Ngã Bảy'              UNION ALL
    SELECT 'Cụm Phụng Hiệp'           UNION ALL
    SELECT 'Cụm Vị Thanh'              UNION ALL
    SELECT 'Cụm Vị Thủy'               UNION ALL
    SELECT 'Cụm Châu Thành 2'         UNION ALL
    SELECT 'Cụm Cù Lao Dung'          UNION ALL
    SELECT 'Cụm Kế Sách'              UNION ALL
    SELECT 'Cụm Long Phú'             UNION ALL
    SELECT 'Cụm Mỹ Tú'               UNION ALL
    SELECT 'Cụm Mỹ Xuyên'             UNION ALL
    SELECT 'Cụm Ngã Năm'              UNION ALL
    SELECT 'Cụm Sóc Trăng'            UNION ALL
    SELECT 'Cụm Thạnh Trị'            UNION ALL
    SELECT 'Cụm Trần Đề'              UNION ALL
    SELECT 'Cụm Vĩnh Châu'
) AS t
WHERE NOT EXISTS (
    SELECT 1 FROM regions r WHERE r.name = t.name
);

-- Xác nhận kết quả
SELECT id, name, created_at FROM regions ORDER BY id;
