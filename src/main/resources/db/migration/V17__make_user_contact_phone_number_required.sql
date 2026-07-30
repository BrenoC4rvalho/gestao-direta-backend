ALTER TABLE user_contacts
    ADD COLUMN IF NOT EXISTS phone_number VARCHAR(20);

DO $$
DECLARE
    legacy_user RECORD;
    placeholder_phone VARCHAR(20);
    placeholder_sequence BIGINT := 1;
BEGIN
    FOR legacy_user IN
        SELECT
            user_record.id AS user_id,
            contact.id AS contact_id
        FROM users user_record
        LEFT JOIN user_contacts contact ON contact.user_id = user_record.id
        WHERE contact.id IS NULL
           OR contact.phone_number IS NULL
           OR btrim(contact.phone_number) = ''
        ORDER BY user_record.id
    LOOP
        LOOP
            -- Reserved, unique legacy marker. It is not a real phone number and must be replaced.
            placeholder_phone := 'LEGACY_' || lpad(placeholder_sequence::TEXT, 13, '0');
            placeholder_sequence := placeholder_sequence + 1;

            EXIT WHEN NOT EXISTS (
                SELECT 1
                FROM user_contacts
                WHERE phone_number = placeholder_phone
            );
        END LOOP;

        IF legacy_user.contact_id IS NULL THEN
            INSERT INTO user_contacts (
                user_id,
                phone_number,
                phone_verification_status,
                preferred_channel,
                status,
                created_at,
                updated_at
            )
            VALUES (
                legacy_user.user_id,
                placeholder_phone,
                'NOT_INFORMED',
                'NONE',
                'PENDING',
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            );
        ELSE
            UPDATE user_contacts
            SET phone_number = placeholder_phone,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = legacy_user.contact_id;
        END IF;
    END LOOP;
END $$;

ALTER TABLE user_contacts
    ALTER COLUMN phone_number SET NOT NULL;
