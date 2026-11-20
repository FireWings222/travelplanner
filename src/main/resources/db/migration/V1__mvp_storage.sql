CREATE TABLE travel_plan (
    id UUID PRIMARY KEY,
    destination VARCHAR(40) NOT NULL,
    payload TEXT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL
);

CREATE INDEX travel_plan_updated_idx ON travel_plan (updated_at);

CREATE TABLE tour_product (
    id UUID PRIMARY KEY,
    destination VARCHAR(40) NOT NULL,
    payload TEXT NOT NULL,
    checked_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX tour_product_destination_checked_idx ON tour_product (destination, checked_at);
