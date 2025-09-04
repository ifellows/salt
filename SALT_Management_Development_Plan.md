# SALT Management Software Development Plan

## Project Overview
Node.js-based management server for SALT (System Assisted Link Tracing) that provides:
- REST API for Android tablet survey uploads
- Web UI for study administration
- Survey configuration and management
- Facility and recruitment management
- Data export and audit capabilities

## Technology Stack
- **Backend**: Node.js with Express.js
- **Database**: SQLite for configuration and metadata
- **File Storage**: Local filesystem for survey data (./data directory)
- **Template Engine**: EJS for server-side rendering
- **Authentication**: API key-based for tablets, session-based for web UI
- **Containerization**: Docker
- **Testing**: Jest for unit tests, Supertest for API tests

## Project Structure
```
salt_management/
├── src/
│   ├── api/
│   │   ├── routes/
│   │   │   ├── auth.js          # Authentication endpoints
│   │   │   ├── surveys.js       # Survey upload/retrieval
│   │   │   ├── config.js        # Survey configuration
│   │   │   ├── facilities.js    # Facility management
│   │   │   ├── recruitment.js   # Recruitment pool & coupons
│   │   │   └── export.js        # Data export endpoints
│   │   ├── middleware/
│   │   │   ├── auth.js          # API key validation
│   │   │   ├── logging.js       # Request/audit logging
│   │   │   └── validation.js    # Input validation
│   │   └── controllers/
│   ├── web/
│   │   ├── routes/
│   │   │   ├── dashboard.js     # Admin dashboard
│   │   │   ├── surveys.js       # Survey management UI
│   │   │   ├── facilities.js    # Facility management UI
│   │   │   ├── recruitment.js   # Recruitment management UI
│   │   │   └── data.js          # Data viewing/editing UI
│   │   └── views/
│   │       ├── layouts/
│   │       ├── partials/
│   │       └── pages/
│   ├── models/
│   │   ├── database.js          # SQLite connection
│   │   ├── survey.js            # Survey model
│   │   ├── facility.js          # Facility model
│   │   ├── recruitment.js       # Recruitment/coupon model
│   │   └── audit.js             # Audit log model
│   ├── services/
│   │   ├── dataStorage.js       # File-based data storage
│   │   ├── exportService.js     # CSV export logic
│   │   ├── auditService.js      # Audit logging
│   │   └── validationService.js # Business logic validation
│   ├── utils/
│   │   ├── crypto.js            # API key generation
│   │   └── fileManager.js       # File operations
│   └── app.js                    # Express app setup
├── data/                         # All persistent data
│   ├── database/
│   │   └── salt.db              # SQLite database
│   ├── surveys/                  # Survey JSON files
│   │   └── YYYY-MM/             # Organized by date
│   ├── exports/                  # Generated CSV exports
│   └── audit/                    # Audit logs
├── public/
│   ├── css/
│   └── js/
├── config/
│   ├── default.json
│   └── production.json
├── tests/
├── Dockerfile
├── docker-compose.yml
├── package.json
└── README.md
```

## Development Phases

### Phase 1: Foundation (Week 1)
- [ ] Initialize Node.js project with Express
- [ ] Set up SQLite database schema
- [ ] Create data directory structure
- [ ] Implement basic authentication (API keys for tablets, sessions for web)
- [ ] Set up audit logging system
- [ ] Create Docker configuration

### Phase 2: Core API (Week 1-2)
- [ ] Survey upload endpoint (POST /api/surveys)
- [ ] API key validation middleware
- [ ] Store survey data as JSON files
- [ ] Update upload status in SQLite
- [ ] Basic error handling and logging

### Phase 3: Survey Configuration (Week 2)
- [ ] Database schema for questions, options, skip logic
- [ ] CRUD API for survey configuration
- [ ] Survey versioning support
- [ ] Validation of survey structure

### Phase 4: Web UI Foundation (Week 2-3)
- [ ] Admin login page
- [ ] Dashboard with upload statistics
- [ ] Survey configuration interface
- [ ] Basic navigation and layout

### Phase 5: Facility Management (Week 3)
- [ ] Facility registration and management
- [ ] API key generation for facilities
- [ ] Facility-survey association
- [ ] Upload tracking by facility

### Phase 6: Recruitment Management (Week 3-4)
- [ ] Recruitment pool configuration
- [ ] Coupon generation and tracking
- [ ] Sampling rate controls
- [ ] Recruitment chain visualization

### Phase 7: Data Management (Week 4)
- [ ] Data viewing interface with pagination
- [ ] Data editing with audit trail
- [ ] CSV export functionality
- [ ] Bulk operations support

### Phase 8: Testing & Documentation (Week 4-5)
- [ ] Unit tests for all services
- [ ] API integration tests
- [ ] API documentation
- [ ] Deployment guide

## Database Schema (SQLite)

```sql
-- Core configuration tables
CREATE TABLE facilities (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    location TEXT,
    api_key TEXT UNIQUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE surveys (
    id INTEGER PRIMARY KEY,
    version INTEGER NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    is_active BOOLEAN DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE questions (
    id INTEGER PRIMARY KEY,
    survey_id INTEGER,
    question_index INTEGER,
    short_name TEXT,
    question_text TEXT NOT NULL,
    question_type TEXT,
    validation_script TEXT,
    pre_script TEXT,
    audio_file TEXT,
    FOREIGN KEY (survey_id) REFERENCES surveys(id)
);

CREATE TABLE options (
    id INTEGER PRIMARY KEY,
    question_id INTEGER,
    option_index INTEGER,
    option_text TEXT NOT NULL,
    option_value TEXT,
    audio_file TEXT,
    FOREIGN KEY (question_id) REFERENCES questions(id)
);

-- Upload tracking
CREATE TABLE uploads (
    id INTEGER PRIMARY KEY,
    survey_response_id TEXT UNIQUE NOT NULL,
    facility_id INTEGER,
    upload_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    file_path TEXT,
    status TEXT,
    FOREIGN KEY (facility_id) REFERENCES facilities(id)
);

-- Recruitment management
CREATE TABLE recruitment_pools (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    criteria TEXT,
    target_size INTEGER,
    sampling_rate REAL,
    is_active BOOLEAN DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE coupons (
    id INTEGER PRIMARY KEY,
    code TEXT UNIQUE NOT NULL,
    pool_id INTEGER,
    distributor_survey_id TEXT,
    recipient_survey_id TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    used_at TIMESTAMP,
    FOREIGN KEY (pool_id) REFERENCES recruitment_pools(id)
);

-- Audit logging
CREATE TABLE audit_log (
    id INTEGER PRIMARY KEY,
    user_id TEXT,
    action TEXT NOT NULL,
    entity_type TEXT,
    entity_id TEXT,
    old_value TEXT,
    new_value TEXT,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Admin users
CREATE TABLE admin_users (
    id INTEGER PRIMARY KEY,
    username TEXT UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    is_active BOOLEAN DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP
);
```

## API Endpoints

### Authentication
- `POST /api/auth/login` - Admin login
- `POST /api/auth/logout` - Admin logout

### Survey Upload (Tablet API)
- `POST /api/surveys` - Upload survey data (requires API key)
- `GET /api/surveys/config` - Get current survey configuration

### Admin API
- `GET /api/admin/uploads` - List all uploads with filters
- `GET /api/admin/uploads/:id` - Get specific upload details
- `POST /api/admin/facilities` - Create facility
- `GET /api/admin/facilities` - List facilities
- `PUT /api/admin/facilities/:id` - Update facility
- `DELETE /api/admin/facilities/:id` - Delete facility
- `POST /api/admin/facilities/:id/regenerate-key` - New API key

### Survey Configuration
- `GET /api/admin/surveys` - List survey versions
- `POST /api/admin/surveys` - Create new survey version
- `GET /api/admin/surveys/:id` - Get survey details
- `PUT /api/admin/surveys/:id` - Update survey
- `POST /api/admin/surveys/:id/activate` - Set as active version

### Data Management
- `GET /api/admin/data` - Query survey responses
- `PUT /api/admin/data/:id` - Edit survey response (with audit)
- `GET /api/admin/export` - Export data as CSV
- `GET /api/admin/audit` - View audit log

### Recruitment
- `GET /api/admin/recruitment/pools` - List recruitment pools
- `POST /api/admin/recruitment/pools` - Create pool
- `PUT /api/admin/recruitment/pools/:id` - Update pool
- `GET /api/admin/recruitment/coupons` - List coupons
- `POST /api/admin/recruitment/coupons/generate` - Generate coupons

## Key Features

### 1. Data Storage Strategy
- SQLite for all configuration and metadata
- JSON files for survey responses (organized by date)
- All data contained in `./data` directory for easy backup
- Replacing `./data` directory restores complete state

### 2. Audit System
- Every data modification logged
- Tracks user, timestamp, old/new values
- Audit logs stored both in SQLite and as JSON files
- Cannot be disabled or deleted

### 3. Survey Upload Flow
1. Tablet sends POST to `/api/surveys` with API key
2. Server validates API key against facility
3. Survey data saved as JSON file
4. Upload record created in SQLite
5. Success/failure returned to tablet

### 4. Security Considerations
- API keys stored as hashed values
- Admin passwords using bcrypt
- Input validation on all endpoints
- SQL injection prevention via parameterized queries
- XSS prevention in web UI
- CORS configuration for API access

## Docker Configuration

```dockerfile
FROM node:18-alpine
WORKDIR /app
COPY package*.json ./
RUN npm ci --only=production
COPY . .
VOLUME ["/app/data"]
EXPOSE 3000
CMD ["node", "src/app.js"]
```

## Development Timeline
- **Week 1**: Foundation + Core API
- **Week 2**: Survey Configuration + Web UI Foundation  
- **Week 3**: Facility & Recruitment Management
- **Week 4**: Data Management + Testing
- **Week 5**: Documentation + Deployment prep

## Success Criteria
- [ ] Tablets can upload surveys via API
- [ ] Admin can configure survey questions and flow
- [ ] Admin can manage facilities and generate API keys
- [ ] Admin can export data as CSV
- [ ] All data modifications are audited
- [ ] System state fully restorable from `./data` directory
- [ ] Docker container runs without external dependencies
- [ ] API documentation complete
- [ ] 80% test coverage

## Next Steps
1. Create salt_management directory
2. Initialize Node.js project
3. Set up Express with EJS
4. Create SQLite schema
5. Implement survey upload endpoint
6. Build admin authentication