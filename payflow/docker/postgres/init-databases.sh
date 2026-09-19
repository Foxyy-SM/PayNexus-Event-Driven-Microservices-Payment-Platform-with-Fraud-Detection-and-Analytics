#!/bin/bash
set -e
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    CREATE DATABASE paynexus_users;
    CREATE DATABASE paynexus_wallets;
    CREATE DATABASE paynexus_payments;
    CREATE DATABASE paynexus_fraud;
    CREATE DATABASE paynexus_notifications;
    CREATE DATABASE paynexus_transactions;
EOSQL
