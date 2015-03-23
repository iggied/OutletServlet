package com.iggie.managerdeviceapp;

import java.util.ArrayList;
import java.util.List;




    class Staff {
        public String type;
        public String outletId;
        public String createdBy;
        public String createdOn;
        public StaffDetailList data;

        public Staff() {
        }
    }

    class StaffDetail {
        public String id;
        public String name;
        public String pin;

        public StaffDetail() {
        }
    }

    class StaffDetailList extends ArrayList<StaffDetail> {}



    class Tables {
        public String type;
        public String outletId;
        public String createdBy;
        public String createdOn;
        public List<TableDetails> data;

        public class TableDetails {
            public String name;
            public List<Table> tables;
        }

        public class Table {
            public String tableNumber;
            public String tableXpos;
            public String tableYpos;
        }
    }


