-- File này chạy TỰ ĐỘNG khi MySQL container khởi động lần đầu
-- Nếu volume mysql_data đã có data → file này KHÔNG chạy lại

CREATE DATABASE IF NOT EXISTS groceryflow_users;
CREATE DATABASE IF NOT EXISTS groceryflow_products;
CREATE DATABASE IF NOT EXISTS groceryflow_orders;
CREATE DATABASE IF NOT EXISTS groceryflow_imports;
CREATE DATABASE IF NOT EXISTS groceryflow_reports;
CREATE DATABASE IF NOT EXISTS groceryflow_audit;

-- Cấp quyền cho user 'groceryflow' trên từng database
-- '%' = cho phép kết nối từ bất kỳ host nào (cần thiết trong Docker network)
GRANT ALL PRIVILEGES ON groceryflow_users.*    TO 'groceryflow'@'%';
GRANT ALL PRIVILEGES ON groceryflow_products.* TO 'groceryflow'@'%';
GRANT ALL PRIVILEGES ON groceryflow_orders.*   TO 'groceryflow'@'%';
GRANT ALL PRIVILEGES ON groceryflow_imports.*  TO 'groceryflow'@'%';
GRANT ALL PRIVILEGES ON groceryflow_reports.*  TO 'groceryflow'@'%';
GRANT ALL PRIVILEGES ON groceryflow_audit.*    TO 'groceryflow'@'%';

FLUSH PRIVILEGES;
