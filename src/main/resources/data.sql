-- Insert roles
INSERT INTO public.roles (role_name, description, created_at, updated_at) VALUES
                                                                       ('ROLE_ADMIN', 'System administrator', NOW(), NOW()),
                                                                       ('ROLE_CUSTOMER', 'Regular customer', NOW(), NOW()),
                                                                       ('ROLE_STAFF', 'Bank staff', NOW(), NOW()),
                                                                       ('ROLE_MANAGER', 'Bank manager', NOW(), NOW());

-- Insert users
INSERT INTO users (id, username, password, full_name, email, phone, date_of_birth, identity_number, status, created_at, updated_at, version) VALUES
                                                                                                                                                 ('u1', 'admin1', '$2a$10$rPiEAgSK4ANCa8RxMNM8T.LqsGk.czPCa7OXoicX7CsWqHxQxwh1O', 'Admin User', 'admin@bank.com', '0901234567', '1990-01-01', '123456789012', 'ACTIVE', NOW(), NOW(), 0),
                                                                                                                                                 ('u2', 'customer1', '$2a$10$rPiEAgSK4ANCa8RxMNM8T.LqsGk.czPCa7OXoicX7CsWqHxQxwh1O', 'John Doe', 'john@example.com', '0901234568', '1992-03-15', '123456789013', 'ACTIVE', NOW(), NOW(), 0),
                                                                                                                                                 ('u3', 'customer2', '$2a$10$rPiEAgSK4ANCa8RxMNM8T.LqsGk.czPCa7OXoicX7CsWqHxQxwh1O', 'Jane Smith', 'jane@example.com', '0901234569', '1995-07-20', '123456789014', 'ACTIVE', NOW(), NOW(), 0),
                                                                                                                                                 ('u4', 'staff1', '$2a$10$rPiEAgSK4ANCa8RxMNM8T.LqsGk.czPCa7OXoicX7CsWqHxQxwh1O', 'Staff One', 'staff1@bank.com', '0901234570', '1988-12-10', '123456789015', 'ACTIVE', NOW(), NOW(), 0);

-- Insert user_roles
INSERT INTO user_roles (user_id, role_id) VALUES
                                              ('u1', 1), -- admin role
                                              ('u2', 2), -- customer role
                                              ('u3', 2), -- customer role
                                              ('u4', 3); -- staff role

-- Insert accounts
INSERT INTO accounts (id, user_id, account_number, balance, status, created_at, updated_at, version) VALUES
                                                                                                         ('a1', 'u2', '0012024030100001', 1000000.00, 'ACTIVE', NOW(), NOW(), 0),
                                                                                                         ('a2', 'u2', '0012024030100002', 500000.00, 'ACTIVE', NOW(), NOW(), 0),
                                                                                                         ('a3', 'u3', '0012024030100003', 750000.00, 'ACTIVE', NOW(), NOW(), 0),
                                                                                                         ('a4', 'u3', '0012024030100004', 250000.00, 'ACTIVE', NOW(), NOW(), 0);

-- Insert transactions cho TRANSFER
INSERT INTO transactions (
    transaction_id,
    from_account_id,
    to_account_id,
    amount,
    transaction_type,
    status,
    description,
    created_at,
    updated_at
) VALUES
      ('t1', 'a1', 'a3', 100000.00, 'TRANSFER', 'COMPLETED', 'Chuyển tiền học phí', NOW(), NOW()),
      ('t2', 'a2', 'a4', 500000.00, 'TRANSFER', 'COMPLETED', 'Chuyển tiền thuê nhà', NOW(), NOW()),
      ('t3', 'a3', 'a1', 300000.00, 'TRANSFER', 'COMPLETED', 'Hoàn tiền', NOW(), NOW()),
      ('t4', 'a4', 'a2', 150000.00, 'TRANSFER', 'FAILED', 'Chuyển tiền thất bại', NOW(), NOW()),
      ('t5', 'a1', 'a4', 250000.00, 'TRANSFER', 'PENDING', 'Đang xử lý', NOW(), NOW());

-- Insert transactions cho DEPOSIT
INSERT INTO transactions (
    transaction_id,
    to_account_id,
    amount,
    transaction_type,
    status,
    description,
    created_at,
    updated_at
) VALUES
      ('t6', 'a1', 1000000.00, 'DEPOSIT', 'COMPLETED', 'Nạp tiền ATM', NOW(), NOW()),
      ('t7', 'a2', 2000000.00, 'DEPOSIT', 'COMPLETED', 'Nạp tiền quầy', NOW(), NOW()),
      ('t8', 'a3', 1500000.00, 'DEPOSIT', 'PENDING', 'Nạp tiền đang xử lý', NOW(), NOW());

-- Insert transactions cho WITHDRAWAL
INSERT INTO transactions (
    transaction_id,
    from_account_id,
    amount,
    transaction_type,
    status,
    description,
    created_at,
    updated_at
) VALUES
      ('t9', 'a1', 500000.00, 'WITHDRAWAL', 'COMPLETED', 'Rút tiền ATM', NOW(), NOW()),
      ('t10', 'a2', 1000000.00, 'WITHDRAWAL', 'COMPLETED', 'Rút tiền quầy', NOW(), NOW()),
      ('t11', 'a3', 750000.00, 'WITHDRAWAL', 'FAILED', 'Rút tiền thất bại', NOW(), NOW());


-- Insert loyalty_points
INSERT INTO loyalty_points (
    id,
    user_id,
    total_points,
    points_earned,
    points_spent,
    tier_status,
    created_at,
    updated_at,
    version
) VALUES
      ('lp1', 'u1', 1000, 1200, 200, 'SILVER', NOW(), NOW(), 0),
      ('lp2', 'u2', 2500, 3000, 500, 'GOLD', NOW(), NOW(), 0),
      ('lp3', 'u3', 100, 100, 0, 'BRONZE', NOW(), NOW(), 0),
      ('lp4', 'u4', 5000, 5500, 500, 'PLATINUM', NOW(), NOW(), 0);

-- Insert point_transactions chỉ cho các user hiện có
INSERT INTO point_transactions (
    transaction_id,
    user_id,
    points,
    transaction_type,
    description,
    created_at
) VALUES
      ('pt1', 'u1', 100, 'EARNED', 'Welcome bonus', NOW()),
      ('pt2', 'u1', 50, 'SPENT', 'Redeem gift card', NOW()),
      ('pt3', 'u2', 200, 'EARNED', 'Monthly transaction bonus', NOW()),
      ('pt4', 'u2', 100, 'SPENT', 'Redeem voucher', NOW()),
      ('pt5', 'u3', 150, 'EARNED', 'Special promotion', NOW()),
      ('pt6', 'u4', 300, 'EARNED', 'Birthday bonus', NOW()),
      ('pt7', 'u4', 200, 'SPENT', 'Redeem cashback', NOW()),
      ('pt8', 'u1', 50, 'EXPIRED', 'Points expired', NOW()),
      ('pt9', 'u2', 100, 'ADJUSTED', 'Manual adjustment', NOW());