CREATE TABLE dim_region (id BIGINT PRIMARY KEY, region_name VARCHAR(50) NOT NULL, province VARCHAR(50) NOT NULL, city VARCHAR(50) NOT NULL);
CREATE TABLE fact_order (id BIGINT PRIMARY KEY, order_no VARCHAR(40) NOT NULL UNIQUE, order_date DATE NOT NULL, total_amount DECIMAL(14,2) NOT NULL);
CREATE TABLE fact_order_item (id BIGINT PRIMARY KEY, order_id BIGINT NOT NULL, quantity INT NOT NULL, CONSTRAINT fk_item_order FOREIGN KEY (order_id) REFERENCES fact_order(id));
INSERT INTO dim_region VALUES (1, 'East', 'Zhejiang', 'Hangzhou'), (2, 'South', 'Guangdong', 'Shenzhen');
INSERT INTO fact_order VALUES (1, 'ORDER-001', '2026-07-01', 120.00), (2, 'ORDER-002', '2026-07-02', 200.00);
INSERT INTO fact_order_item VALUES (1, 1, 10), (2, 2, 2);
CREATE USER 'chatbi_reader'@'%' IDENTIFIED BY 'reader-test-password';
GRANT SELECT ON sample_sales.* TO 'chatbi_reader'@'%';
