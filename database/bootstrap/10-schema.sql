-- Fresh demo schema at cb8749d, exported from the JPA mappings using Hibernate 6.6.53.
-- The final ALTER statements carry indexes and CHECK constraints from the tracked migrations.

create table annual_leave_balancetbl (
    granted_days decimal(4,1) not null,
    leave_year integer not null,
    remaining_days decimal(4,1) not null,
    annual_leave_balance_id bigint not null auto_increment,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    user_id bigint not null,
    version bigint not null,
    primary key (annual_leave_balance_id)
) engine=InnoDB;

create table annual_leave_historytbl (
    balance_after decimal(4,1) not null,
    days_delta decimal(4,1) not null,
    actor_id bigint,
    annual_leave_history_id bigint not null auto_increment,
    balance_id bigint not null,
    cancellation_id bigint,
    created_at datetime(6) not null,
    leave_request_id bigint,
    source_key varchar(100) not null,
    action_type enum ('GRANT','RESTORE','USE') not null,
    primary key (annual_leave_history_id)
) engine=InnoDB;

create table approval_attachmenttbl (
    approval_attachment_id bigint not null auto_increment,
    approval_id bigint not null,
    created_at datetime(6) not null,
    file_size bigint not null,
    uploader_id bigint not null,
    file_extension varchar(20) not null,
    content_type varchar(100),
    storage_key varchar(500) not null,
    original_file_name varchar(255) not null,
    primary key (approval_attachment_id)
) engine=InnoDB;

create table approval_cctbl (
    approval_cc_id bigint not null auto_increment,
    approval_id bigint not null,
    created_at datetime(6) not null,
    user_id bigint not null,
    primary key (approval_cc_id)
) engine=InnoDB;

create table approval_delegationtbl (
    end_date date not null,
    start_date date not null,
    approval_delegation_id bigint not null auto_increment,
    canceled_at datetime(6),
    created_at datetime(6) not null,
    created_by bigint not null,
    delegatee_id bigint not null,
    delegator_id bigint not null,
    primary key (approval_delegation_id)
) engine=InnoDB;

create table approval_document_typetbl (
    active bit not null,
    is_system bit not null,
    sort_order integer not null,
    approval_document_type_id bigint not null auto_increment,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    version bigint not null,
    type_code varchar(30) not null,
    type_name varchar(100) not null,
    description varchar(500),
    content_template LONGTEXT,
    behavior_type enum ('ATTENDANCE_CORRECTION','GENERAL','LEAVE_CANCELLATION','LEAVE_REQUEST') not null,
    primary key (approval_document_type_id)
) engine=InnoDB;

create table approval_historytbl (
    action_step integer not null,
    actor_id bigint not null,
    approval_history_id bigint not null auto_increment,
    approval_id bigint not null,
    created_at datetime(6) not null,
    comment TEXT,
    action_type enum ('APPROVED','CANCELED','REJECTED','SUBMITTED','TERMINATED') not null,
    primary key (approval_history_id)
) engine=InnoDB;

create table approval_linetbl (
    approval_step integer not null,
    approval_id bigint not null,
    approval_line_id bigint not null auto_increment,
    approver_id bigint not null,
    created_at datetime(6) not null,
    processed_at datetime(6),
    processed_by_id bigint,
    approval_comment TEXT,
    approval_status enum ('APPROVED','PENDING','REJECTED','WAITING') not null,
    primary key (approval_line_id)
) engine=InnoDB;

create table approval_template_cctbl (
    approval_template_cc_id bigint not null auto_increment,
    approval_template_id bigint not null,
    baseline_department_id bigint,
    user_id bigint not null,
    primary key (approval_template_cc_id)
) engine=InnoDB;

create table approval_template_linetbl (
    approval_step integer not null,
    approval_template_id bigint not null,
    approval_template_line_id bigint not null auto_increment,
    approver_id bigint not null,
    baseline_department_id bigint,
    primary key (approval_template_line_id)
) engine=InnoDB;

create table approval_templatetbl (
    approval_template_id bigint not null auto_increment,
    created_at datetime(6) not null,
    created_by bigint not null,
    department_id bigint,
    updated_at datetime(6) not null,
    updated_by bigint not null,
    version bigint not null,
    template_name varchar(100) not null,
    description varchar(500),
    scope enum ('COMMON','DEPARTMENT') not null,
    status enum ('ACTIVE','INACTIVE') not null,
    validation_status enum ('BLOCKED','VALID') not null,
    primary key (approval_template_id)
) engine=InnoDB;

create table approvaltbl (
    current_step integer,
    approval_id bigint not null auto_increment,
    completed_at datetime(6),
    created_at datetime(6) not null,
    document_type_id bigint,
    submitted_at datetime(6),
    updated_at datetime(6) not null,
    writer_id bigint not null,
    document_type varchar(30) not null,
    title varchar(200) not null,
    content LONGTEXT not null,
    approval_status enum ('APPROVED','CANCELED','DRAFT','IN_PROGRESS','REJECTED','TERMINATED') not null,
    primary key (approval_id)
) engine=InnoDB;

create table attendance_change_historytbl (
    after_early_leave bit not null,
    after_late bit not null,
    attendance_date date not null,
    before_early_leave bit,
    before_late bit,
    actor_id bigint not null,
    after_check_in_at datetime(6) not null,
    after_check_out_at datetime(6),
    approval_id bigint,
    attendance_change_history_id bigint not null auto_increment,
    before_check_in_at datetime(6),
    before_check_out_at datetime(6),
    created_at datetime(6) not null,
    user_id bigint not null,
    source varchar(30) not null,
    reason varchar(1000) not null,
    primary key (attendance_change_history_id)
) engine=InnoDB;

create table attendance_correctiontbl (
    attendance_date date not null,
    pending_date date,
    approval_id bigint not null,
    attendance_correction_id bigint not null auto_increment,
    created_at datetime(6) not null,
    original_attendance_id bigint,
    original_check_in_at datetime(6),
    original_check_out_at datetime(6),
    original_version bigint,
    requested_check_in_at datetime(6) not null,
    requested_check_out_at datetime(6),
    user_id bigint not null,
    reason varchar(1000) not null,
    primary key (attendance_correction_id)
) engine=InnoDB;

create table attendancetbl (
    attendance_date date not null,
    is_early_leave bit not null,
    is_late bit not null,
    late_grace_minutes integer not null,
    attendance_id bigint not null auto_increment,
    check_in_at datetime(6) not null,
    check_out_at datetime(6),
    created_at datetime(6) not null,
    scheduled_check_in_at datetime(6) not null,
    scheduled_check_out_at datetime(6) not null,
    updated_at datetime(6) not null,
    user_id bigint not null,
    version bigint not null,
    work_policy_id bigint not null,
    primary key (attendance_id)
) engine=InnoDB;

create table departmenttbl (
    created_at datetime(6) not null,
    department_id bigint not null auto_increment,
    manager_user_id bigint,
    parent_department_id bigint,
    updated_at datetime(6) not null,
    department_name varchar(100) not null,
    status enum ('ACTIVE','DELETED','INACTIVE') not null,
    primary key (department_id)
) engine=InnoDB;

create table employee_number_sequencetbl (
    hire_year integer not null,
    last_sequence integer not null,
    employee_number_sequence_id bigint not null auto_increment,
    job_family varchar(20) not null,
    primary key (employee_number_sequence_id)
) engine=InnoDB;

create table leave_cancellationtbl (
    approval_id bigint not null,
    created_at datetime(6) not null,
    leave_cancellation_id bigint not null auto_increment,
    leave_request_id bigint not null,
    pending_leave_request_id bigint,
    updated_at datetime(6) not null,
    version bigint not null,
    reason varchar(1000) not null,
    primary key (leave_cancellation_id)
) engine=InnoDB;

create table leave_request_daytbl (
    days decimal(2,1) not null,
    leave_date date not null,
    created_at datetime(6) not null,
    leave_request_day_id bigint not null auto_increment,
    leave_request_id bigint not null,
    leave_period enum ('AM','FULL','PM') not null,
    primary key (leave_request_day_id)
) engine=InnoDB;

create table leave_requesttbl (
    total_days decimal(4,1) not null,
    approval_id bigint not null,
    balance_id bigint not null,
    canceled_at datetime(6),
    created_at datetime(6) not null,
    leave_request_id bigint not null auto_increment,
    updated_at datetime(6) not null,
    user_id bigint not null,
    version bigint not null,
    reason varchar(1000) not null,
    primary key (leave_request_id)
) engine=InnoDB;

create table notificationtbl (
    approval_id bigint,
    approval_template_id bigint,
    created_at datetime(6) not null,
    notification_id bigint not null auto_increment,
    read_at datetime(6),
    recipient_id bigint not null,
    event_key varchar(64) not null,
    title varchar(200) not null,
    message varchar(500) not null,
    notification_type enum ('APPROVAL_CANCELED','APPROVAL_COMPLETED','APPROVAL_REFERENCE','APPROVAL_REJECTED','APPROVAL_REQUEST','APPROVAL_TEMPLATE_BLOCKED','APPROVAL_TERMINATED') not null,
    primary key (notification_id)
) engine=InnoDB;

create table positiontbl (
    position_order integer not null,
    created_at datetime(6) not null,
    position_id bigint not null auto_increment,
    updated_at datetime(6) not null,
    position_name varchar(50) not null,
    primary key (position_id)
) engine=InnoDB;

create table usertbl (
    hire_date date,
    created_at datetime(6) not null,
    department_id bigint,
    position_id bigint,
    updated_at datetime(6) not null,
    user_id bigint not null auto_increment,
    employee_no varchar(100) not null,
    user_name varchar(100) not null,
    user_phone varchar(100),
    login_id varchar(200) not null,
    user_email varchar(200),
    user_pass varchar(200) not null,
    job_family enum ('CORPORATE','TECHNICAL'),
    status enum ('ACTIVE','DELETED','INACTIVE') not null,
    user_role enum ('HR_MANAGER','SUPER_ADMIN','USER') not null,
    primary key (user_id)
) engine=InnoDB;

create table work_policy_daytbl (
    is_working bit not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    work_policy_day_id bigint not null auto_increment,
    work_policy_id bigint not null,
    day_of_week enum ('FRIDAY','MONDAY','SATURDAY','SUNDAY','THURSDAY','TUESDAY','WEDNESDAY') not null,
    primary key (work_policy_day_id)
) engine=InnoDB;

create table work_policytbl (
    break_end_time time(6) not null,
    break_start_time time(6) not null,
    effective_from date not null,
    effective_to date,
    late_grace_minutes integer not null,
    work_end_time time(6) not null,
    work_start_time time(6) not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    work_policy_id bigint not null auto_increment,
    policy_name varchar(100) not null,
    status enum ('ACTIVE','INACTIVE') not null,
    primary key (work_policy_id)
) engine=InnoDB;

create table work_schedule_exceptiontbl (
    active bit not null,
    exception_date date not null,
    working bit not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    version bigint not null,
    work_schedule_exception_id bigint not null auto_increment,
    name varchar(100) not null,
    primary key (work_schedule_exception_id)
) engine=InnoDB;

alter table annual_leave_balancetbl
   add constraint uk_annual_leave_balance_user_year unique (user_id, leave_year);

alter table annual_leave_historytbl
   add constraint UKmg5n4bnkjdmt1rqq8yti6ng12 unique (source_key);

alter table approval_attachmenttbl
   add constraint UK75sea7m0g7y1lub6c5d9kbncl unique (storage_key);

alter table approval_cctbl
   add constraint uk_approval_cctbl_approval_user unique (approval_id, user_id);

create index idx_approval_historytbl_created
   on approval_historytbl (created_at desc, approval_history_id desc);

create index idx_approval_linetbl_approver_status
   on approval_linetbl (approver_id, approval_status);

create index idx_approval_linetbl_processor_processed
   on approval_linetbl (processed_by_id, processed_at desc, approval_line_id desc, approval_status, approval_id);

alter table approval_linetbl
   add constraint uk_approval_linetbl_approval_step unique (approval_id, approval_step);

alter table approval_linetbl
   add constraint uk_approval_linetbl_approval_approver unique (approval_id, approver_id);

alter table approval_template_cctbl
   add constraint uk_template_cc_user unique (approval_template_id, user_id);

alter table approval_template_linetbl
   add constraint uk_template_line_step unique (approval_template_id, approval_step);

alter table approval_template_linetbl
   add constraint uk_template_line_approver unique (approval_template_id, approver_id);

alter table attendance_correctiontbl
   add constraint UKosi3s1l39do0j9ppcilx1tm3b unique (user_id, pending_date);

alter table attendance_correctiontbl
   add constraint UKmsr533pdt0o5mqpape4yl3rko unique (approval_id);

alter table attendancetbl
   add constraint uk_attendance_user_date unique (user_id, attendance_date);

alter table employee_number_sequencetbl
   add constraint uk_employee_number_sequence_family_year unique (job_family, hire_year);

alter table leave_cancellationtbl
   add constraint UKgqari7xifv91dmwt3cwhpgwre unique (approval_id);

alter table leave_cancellationtbl
   add constraint UKjtpeyv0wppf6rfq2ltst8mc6d unique (pending_leave_request_id);

alter table leave_requesttbl
   add constraint UK4sxo2hkoxewxjsq25m0gr9240 unique (approval_id);

alter table positiontbl
   add constraint uk_positiontbl_position_name unique (position_name);

alter table positiontbl
   add constraint uk_positiontbl_position_order unique (position_order);

alter table usertbl
   add constraint uk_usertbl_login_id unique (login_id);

alter table usertbl
   add constraint uk_usertbl_employee_no unique (employee_no);

alter table work_schedule_exceptiontbl
   add constraint UKesfp6ca4v7rcpubt2tx2ypxgh unique (exception_date);

alter table annual_leave_balancetbl
   add constraint FKqni7pk3mgdeq1madcm38tos6h
   foreign key (user_id)
   references usertbl (user_id);

alter table annual_leave_historytbl
   add constraint FKj4lgt5kmwhc0gw5mif26rqdki
   foreign key (balance_id)
   references annual_leave_balancetbl (annual_leave_balance_id);

alter table approval_attachmenttbl
   add constraint FKgykt5wlh0wh9fniwnkq46fw39
   foreign key (approval_id)
   references approvaltbl (approval_id) on delete cascade;

alter table approval_attachmenttbl
   add constraint FKjqq8tu1pnaux7mibjf7vo5ttv
   foreign key (uploader_id)
   references usertbl (user_id);

alter table approval_cctbl
   add constraint FK2bvv92v9gpo2rainwsi7xvuq
   foreign key (approval_id)
   references approvaltbl (approval_id);

alter table approval_cctbl
   add constraint FKbo4rj66ws53e5e2mqjnhmo6p0
   foreign key (user_id)
   references usertbl (user_id);

alter table approval_delegationtbl
   add constraint FKt188gh703v7sluo35ftrsbeqb
   foreign key (created_by)
   references usertbl (user_id);

alter table approval_delegationtbl
   add constraint FK3k6inba6kyjx5n2lrt6iklgsa
   foreign key (delegatee_id)
   references usertbl (user_id);

alter table approval_delegationtbl
   add constraint FKf5oouk60qk6in1inbm3k5vmms
   foreign key (delegator_id)
   references usertbl (user_id);

alter table approval_historytbl
   add constraint FKt601syu8c5mkw65vgkv3kgxaa
   foreign key (actor_id)
   references usertbl (user_id);

alter table approval_historytbl
   add constraint FKono31ma1l2qhtrns0sd8d7xo8
   foreign key (approval_id)
   references approvaltbl (approval_id);

alter table approval_linetbl
   add constraint FKgbpmjhplpgobhfr781w122wq
   foreign key (approval_id)
   references approvaltbl (approval_id);

alter table approval_linetbl
   add constraint FK458exktvbx3tg1xf72mcc2i1r
   foreign key (approver_id)
   references usertbl (user_id);

alter table approval_linetbl
   add constraint FKr9793uc4on5cn86thtmcaxg7g
   foreign key (processed_by_id)
   references usertbl (user_id);

alter table approval_template_cctbl
   add constraint FK5e07siiny5h897ea394fg1gcl
   foreign key (approval_template_id)
   references approval_templatetbl (approval_template_id);

alter table approval_template_cctbl
   add constraint FKti2580aj4l73c8i7i7po2fdwr
   foreign key (user_id)
   references usertbl (user_id);

alter table approval_template_linetbl
   add constraint FKbu8g0ok8ay2i2o64qe7y5sy4
   foreign key (approval_template_id)
   references approval_templatetbl (approval_template_id);

alter table approval_template_linetbl
   add constraint FKkg98l8dmd8jgbc1etj93enmu6
   foreign key (approver_id)
   references usertbl (user_id);

alter table approval_templatetbl
   add constraint FKj1omfmocw1u1c1bslnn3dvilu
   foreign key (created_by)
   references usertbl (user_id);

alter table approval_templatetbl
   add constraint FKnftoh6385uop1u5nkqbhahy8m
   foreign key (department_id)
   references departmenttbl (department_id);

alter table approval_templatetbl
   add constraint FK1c4vxw9eau5h04k25flfnyaou
   foreign key (updated_by)
   references usertbl (user_id);

alter table approvaltbl
   add constraint FK7yxqb2h66vrpmhdj0vskskef3
   foreign key (document_type_id)
   references approval_document_typetbl (approval_document_type_id);

alter table approvaltbl
   add constraint FKarpqhswectubbi5c8rpu1deqv
   foreign key (writer_id)
   references usertbl (user_id);

alter table attendance_correctiontbl
   add constraint FK5spx26m9fb4bhxti2fvg9p75g
   foreign key (approval_id)
   references approvaltbl (approval_id);

alter table attendance_correctiontbl
   add constraint FKf8osb77nmx5l8uen8xnqhcmk9
   foreign key (user_id)
   references usertbl (user_id);

alter table attendancetbl
   add constraint FK356fkpqx2jlulc4fi95edjg0h
   foreign key (user_id)
   references usertbl (user_id);

alter table attendancetbl
   add constraint FKg6gxcw79we3evjabuji1wbyuv
   foreign key (work_policy_id)
   references work_policytbl (work_policy_id);

alter table departmenttbl
   add constraint FKcnn3dpr5ye5y2nfxx28jrsaim
   foreign key (parent_department_id)
   references departmenttbl (department_id);

alter table leave_cancellationtbl
   add constraint FK8oloho8qa1iyrpv9jdd3x6uqk
   foreign key (approval_id)
   references approvaltbl (approval_id);

alter table leave_cancellationtbl
   add constraint FKtdr4jm3osk28py8de6qqy0eg6
   foreign key (leave_request_id)
   references leave_requesttbl (leave_request_id);

alter table leave_request_daytbl
   add constraint FK3gs5dv6l57jb1s3flxaxhn6my
   foreign key (leave_request_id)
   references leave_requesttbl (leave_request_id);

alter table leave_requesttbl
   add constraint FKeoqiaf3dgisf29jni9ggpcimb
   foreign key (approval_id)
   references approvaltbl (approval_id);

alter table leave_requesttbl
   add constraint FKrd2tlwd4cmrf9vqvjvw5nqkvb
   foreign key (balance_id)
   references annual_leave_balancetbl (annual_leave_balance_id);

alter table leave_requesttbl
   add constraint FKl86ejghnlq2puvtfa77bjg5pl
   foreign key (user_id)
   references usertbl (user_id);

alter table notificationtbl
   add constraint FK7qrq8j1dd9pt0o3d9crfl8cqi
   foreign key (approval_id)
   references approvaltbl (approval_id);

alter table notificationtbl
   add constraint FKjo72t1iuqvvb48oao1dg61ro6
   foreign key (recipient_id)
   references usertbl (user_id);

alter table usertbl
   add constraint FKqnm2ee7xafs7338ymmnqf1g0j
   foreign key (department_id)
   references departmenttbl (department_id);

alter table usertbl
   add constraint FK84qe81ceoo1gi6gpnrn3kxe3b
   foreign key (position_id)
   references positiontbl (position_id);

alter table work_policy_daytbl
   add constraint FKfljo0kigctob94je4dlh9egci
   foreign key (work_policy_id)
   references work_policytbl (work_policy_id) on delete cascade;

ALTER TABLE employee_number_sequencetbl
    ADD CONSTRAINT chk_employee_number_sequence_positive CHECK (last_sequence > 0);

ALTER TABLE approval_attachmenttbl
    ADD CONSTRAINT chk_approval_attachment_file_size CHECK (file_size > 0),
    ADD INDEX idx_approval_attachment_approval (approval_id, approval_attachment_id);

ALTER TABLE work_policytbl
    ADD CONSTRAINT chk_work_policy_work_time CHECK (work_start_time < work_end_time),
    ADD CONSTRAINT chk_work_policy_break_time CHECK (
            break_start_time < break_end_time
            AND break_start_time >= work_start_time
            AND break_end_time <= work_end_time
        ),
    ADD CONSTRAINT chk_work_policy_late_grace CHECK (late_grace_minutes <= 1440),
    ADD CONSTRAINT chk_work_policy_effective_period CHECK (effective_to IS NULL OR effective_to >= effective_from),
    ADD CONSTRAINT chk_work_policy_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    ADD INDEX idx_work_policy_effective_period (status, effective_from, effective_to);

ALTER TABLE attendancetbl
    ADD CONSTRAINT chk_attendance_scheduled_time CHECK (scheduled_check_in_at < scheduled_check_out_at),
    ADD CONSTRAINT chk_attendance_check_time CHECK (check_out_at IS NULL OR check_out_at >= check_in_at),
    ADD CONSTRAINT chk_attendance_late_grace CHECK (late_grace_minutes <= 1440),
    ADD CONSTRAINT chk_attendance_is_late CHECK (is_late IN (0, 1)),
    ADD CONSTRAINT chk_attendance_is_early_leave CHECK (is_early_leave IN (0, 1)),
    ADD INDEX idx_attendance_work_policy (work_policy_id);

ALTER TABLE work_policy_daytbl
    ADD CONSTRAINT chk_work_policy_day_of_week CHECK (
            day_of_week IN (
                'MONDAY',
                'TUESDAY',
                'WEDNESDAY',
                'THURSDAY',
                'FRIDAY',
                'SATURDAY',
                'SUNDAY'
            )
        ),
    ADD CONSTRAINT chk_work_policy_day_is_working CHECK (is_working IN (0, 1)),
    ADD UNIQUE INDEX uk_work_policy_day (work_policy_id, day_of_week);

ALTER TABLE attendance_correctiontbl
    ADD CONSTRAINT chk_correction_pending CHECK (pending_date IS NULL OR pending_date = attendance_date),
    ADD CONSTRAINT chk_correction_time CHECK (DATE(requested_check_in_at) = attendance_date
        AND (requested_check_out_at IS NULL OR (DATE(requested_check_out_at) = attendance_date
        AND requested_check_out_at >= requested_check_in_at))),
    ADD INDEX idx_correction_user_date (user_id, attendance_date);

ALTER TABLE attendance_change_historytbl
    ADD CONSTRAINT chk_attendance_history_source CHECK (
        (source = 'APPROVAL' AND approval_id IS NOT NULL)
        OR (source = 'SUPER_ADMIN_DIRECT' AND approval_id IS NULL)),
    ADD INDEX idx_attendance_history_user_date (user_id, attendance_date, attendance_change_history_id);

ALTER TABLE annual_leave_balancetbl
    ADD CONSTRAINT chk_annual_leave_balance_year CHECK (leave_year BETWEEN 2000 AND 9999),
    ADD CONSTRAINT chk_annual_leave_balance_granted CHECK (granted_days > 0.0 AND granted_days <= 25.0
            AND MOD(granted_days * 10, 5) = 0),
    ADD CONSTRAINT chk_annual_leave_balance_remaining CHECK (remaining_days >= 0.0 AND remaining_days <= granted_days
            AND MOD(remaining_days * 10, 5) = 0),
    ADD INDEX idx_annual_leave_balance_year (leave_year, user_id);

ALTER TABLE leave_requesttbl
    ADD CONSTRAINT chk_leave_request_total_days CHECK (total_days > 0.0 AND total_days <= 25.0
            AND MOD(total_days * 10, 5) = 0),
    ADD CONSTRAINT chk_leave_request_reason CHECK (CHAR_LENGTH(TRIM(reason)) BETWEEN 1 AND 1000),
    ADD INDEX idx_leave_request_user (user_id, leave_request_id),
    ADD INDEX idx_leave_request_balance (balance_id, leave_request_id);

ALTER TABLE leave_request_daytbl
    ADD CONSTRAINT chk_leave_request_day_period CHECK (leave_period IN ('FULL', 'AM', 'PM')),
    ADD CONSTRAINT chk_leave_request_day_amount CHECK ((leave_period = 'FULL' AND days = 1.0)
            OR (leave_period IN ('AM', 'PM') AND days = 0.5)),
    ADD UNIQUE INDEX uk_leave_request_day (leave_request_id, leave_date),
    ADD INDEX idx_leave_request_day_date (leave_date, leave_request_id);

ALTER TABLE leave_cancellationtbl
    ADD CONSTRAINT chk_leave_cancellation_pending CHECK (pending_leave_request_id IS NULL OR pending_leave_request_id = leave_request_id),
    ADD CONSTRAINT chk_leave_cancellation_reason CHECK (CHAR_LENGTH(TRIM(reason)) BETWEEN 1 AND 1000),
    ADD INDEX idx_leave_cancellation_request (leave_request_id, leave_cancellation_id);

ALTER TABLE annual_leave_historytbl
    ADD CONSTRAINT chk_annual_leave_history_type CHECK (action_type IN ('GRANT', 'USE', 'RESTORE')),
    ADD CONSTRAINT chk_annual_leave_history_delta CHECK (days_delta <> 0.0 AND MOD(ABS(days_delta) * 10, 5) = 0
            AND ((action_type = 'USE' AND days_delta < 0.0)
                OR (action_type IN ('GRANT', 'RESTORE') AND days_delta > 0.0))),
    ADD CONSTRAINT chk_annual_leave_history_balance_after CHECK (balance_after >= 0.0 AND balance_after <= 25.0
            AND MOD(balance_after * 10, 5) = 0),
    ADD CONSTRAINT chk_annual_leave_history_reference CHECK ((action_type = 'GRANT'
                AND leave_request_id IS NULL AND cancellation_id IS NULL)
            OR (action_type = 'USE'
                AND leave_request_id IS NOT NULL AND cancellation_id IS NULL)
            OR (action_type = 'RESTORE'
                AND leave_request_id IS NOT NULL AND cancellation_id IS NOT NULL)),
    ADD INDEX idx_annual_leave_history_balance (balance_id, annual_leave_history_id),
    ADD INDEX idx_annual_leave_history_request (leave_request_id),
    ADD INDEX idx_annual_leave_history_cancellation (cancellation_id);

ALTER TABLE approval_document_typetbl
    ADD CONSTRAINT chk_approval_document_type_behavior CHECK (behavior_type IN (
            'GENERAL',
            'ATTENDANCE_CORRECTION',
            'LEAVE_REQUEST',
            'LEAVE_CANCELLATION'
        )),
    ADD CONSTRAINT chk_approval_document_type_sort_order CHECK (sort_order >= 0),
    ADD UNIQUE INDEX uk_approval_document_type_code (type_code),
    ADD UNIQUE INDEX uk_approval_document_type_name (type_name),
    ADD INDEX idx_approval_document_type_active_sort (active, sort_order, approval_document_type_id);

ALTER TABLE approvaltbl
    ADD INDEX idx_approval_document_type_id_status (document_type_id, approval_status, approval_id);
