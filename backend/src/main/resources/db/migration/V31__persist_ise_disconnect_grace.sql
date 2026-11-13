CREATE TABLE ise_disconnect_grace (
    mac_address varchar(32) PRIMARY KEY,
    miss_count integer NOT NULL CHECK (miss_count > 0),
    updated_at timestamptz NOT NULL DEFAULT now()
);
