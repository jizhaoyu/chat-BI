#!/bin/bash
set -euo pipefail

if [[ ! "${SAMPLE_READER_PASSWORD}" =~ ^[A-Za-z0-9._@%+=:-]{12,128}$ ]]; then
  echo "SAMPLE_READER_PASSWORD must be 12-128 characters from the documented safe set" >&2
  exit 1
fi

mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE USER IF NOT EXISTS 'chatbi_reader'@'%' IDENTIFIED BY '${SAMPLE_READER_PASSWORD}';
REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'chatbi_reader'@'%';
GRANT SELECT ON sample_sales.* TO 'chatbi_reader'@'%';
FLUSH PRIVILEGES;
SQL
