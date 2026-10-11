ALTER TABLE pricing_exception_request ADD COLUMN previous_request_id UUID REFERENCES pricing_exception_request (id);
