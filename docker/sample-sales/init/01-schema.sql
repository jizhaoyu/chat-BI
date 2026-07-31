USE sample_sales;

CREATE TABLE dim_region (id BIGINT PRIMARY KEY, region_name VARCHAR(50) NOT NULL, province VARCHAR(50) NOT NULL, city VARCHAR(50) NOT NULL);
CREATE TABLE dim_store (id BIGINT PRIMARY KEY, store_name VARCHAR(100) NOT NULL, region_id BIGINT NOT NULL, opened_at DATE NOT NULL, CONSTRAINT fk_store_region FOREIGN KEY (region_id) REFERENCES dim_region(id));
CREATE TABLE dim_product (id BIGINT PRIMARY KEY, product_name VARCHAR(100) NOT NULL, category VARCHAR(50) NOT NULL, brand VARCHAR(50) NOT NULL, cost_price DECIMAL(12,2) NOT NULL);
CREATE TABLE dim_customer (id BIGINT PRIMARY KEY, customer_name VARCHAR(100) NOT NULL, phone VARCHAR(30), email VARCHAR(120), level VARCHAR(20) NOT NULL, registered_at DATE NOT NULL);
CREATE TABLE fact_order (id BIGINT PRIMARY KEY, order_no VARCHAR(40) NOT NULL UNIQUE, store_id BIGINT NOT NULL, customer_id BIGINT NOT NULL, order_date DATE NOT NULL, status VARCHAR(20) NOT NULL, total_amount DECIMAL(14,2) NOT NULL, CONSTRAINT fk_order_store FOREIGN KEY (store_id) REFERENCES dim_store(id), CONSTRAINT fk_order_customer FOREIGN KEY (customer_id) REFERENCES dim_customer(id));
CREATE TABLE fact_order_item (id BIGINT PRIMARY KEY, order_id BIGINT NOT NULL, product_id BIGINT NOT NULL, quantity INT NOT NULL, unit_price DECIMAL(12,2) NOT NULL, discount_amount DECIMAL(12,2) NOT NULL, CONSTRAINT fk_item_order FOREIGN KEY (order_id) REFERENCES fact_order(id), CONSTRAINT fk_item_product FOREIGN KEY (product_id) REFERENCES dim_product(id));

INSERT INTO dim_region VALUES (1, 'East', 'Zhejiang', 'Hangzhou'), (2, 'South', 'Guangdong', 'Shenzhen');
INSERT INTO dim_store VALUES (1, 'Hangzhou Hub', 1, '2022-01-01'), (2, 'Shenzhen Hub', 2, '2022-06-01');
INSERT INTO dim_product VALUES (1, 'Notebook', 'Office', 'Acme', 12.00), (2, 'Headphones', 'Electronics', 'SoundCo', 80.00);
INSERT INTO dim_customer VALUES (1, 'Demo One', '13800000001', 'demo1@example.test', 'GOLD', '2024-01-01'), (2, 'Demo Two', '13800000002', 'demo2@example.test', 'STANDARD', '2024-02-01');
INSERT INTO fact_order VALUES (1, 'ORDER-001', 1, 1, '2026-07-01', 'PAID', 120.00), (2, 'ORDER-002', 2, 2, '2026-07-02', 'PAID', 200.00);
INSERT INTO fact_order_item VALUES (1, 1, 1, 10, 12.00, 0.00), (2, 2, 2, 2, 100.00, 0.00);
