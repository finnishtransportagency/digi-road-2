CREATE TABLE LANE (
    ID NUMBER,
    LANE_CODE NUMBER(20) NOT NULL,
    CREATED_DATE TIMESTAMP(6) NOT NULL,
    CREATED_BY VARCHAR2(128) NOT NULL,
    MODIFIED_DATE TIMESTAMP(6),
    MODIFIED_BY VARCHAR2(128),
    EXPIRED_DATE TIMESTAMP(6),
    EXPIRED_BY VARCHAR2(128),
    VALID_FROM TIMESTAMP(6),
    VALID_TO TIMESTAMP,
    MUNICIPALITY_CODE NUMBER,
    CONSTRAINT lane_pk PRIMARY KEY (ID)
);
CREATE UNIQUE INDEX LANE_CODE_INDEX ON LANE (LANE_CODE);
CREATE INDEX LANE_MUNICIPALITY_CODE_IDX ON LANE (MUNICIPALITY_CODE);

CREATE TABLE LANE_POSITION (
    ID NUMBER,
    SIDE_CODE NUMBER(5) NOT NULL,
    START_MEASURE NUMBER(8,3) NOT NULL,
    END_MEASURE NUMBER(8,3) NOT NULL,
    LINK_ID NUMBER(10) NOT NULL,
    ADJUSTED_TIMESTAMP NUMBER(38),
    MODIFIED_DATE TIMESTAMP(6),
    CONSTRAINT lane_position_pk PRIMARY KEY (ID)
);
CREATE UNIQUE INDEX LANE_POSITION_LINK_ID_IDX ON LANE_POSITION (LINK_ID);

CREATE TABLE LANE_LINK (
    LANE_ID NUMBER,
    LANE_POSITION_ID NUMBER,
    CONSTRAINT lane_link_pk PRIMARY KEY (LANE_ID, LANE_POSITION_ID),
    CONSTRAINT fk_lane_link_lane FOREIGN KEY(LANE_ID) REFERENCES LANE(ID),
    CONSTRAINT fk_lane_link_lane_position FOREIGN KEY(LANE_POSITION_ID) REFERENCES LANE_POSITION(ID)
);

CREATE TABLE LANE_ATTRIBUTE (
    ID NUMBER,
    LANE_ID NUMBER(38),
    NAME VARCHAR2(128),
    VALUE VARCHAR2(128),
    CREATED_DATE TIMESTAMP(6),
    CREATED_BY VARCHAR2(128),
    MODIFIED_DATE TIMESTAMP(6),
    MODIFIED_BY VARCHAR2(128),
    CONSTRAINT lane_attribute_pk PRIMARY KEY (ID),
    CONSTRAINT fk_lane_attribute_lane FOREIGN KEY(LANE_ID) REFERENCES LANE(ID)
);
