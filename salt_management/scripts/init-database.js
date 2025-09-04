const sqlite3 = require('sqlite3').verbose();
const path = require('path');
const fs = require('fs');

const dbPath = path.join(__dirname, '..', 'data', 'database', 'salt.db');
const dbDir = path.dirname(dbPath);

// Ensure database directory exists
if (!fs.existsSync(dbDir)) {
    fs.mkdirSync(dbDir, { recursive: true });
}

// Create and initialize database
const db = new sqlite3.Database(dbPath, (err) => {
    if (err) {
        console.error('Error opening database:', err);
        process.exit(1);
    }
    console.log('Connected to SQLite database');
});

// SQL statements to create tables
const createTables = `
-- Core configuration tables
CREATE TABLE IF NOT EXISTS facilities (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    location TEXT,
    api_key TEXT UNIQUE NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS surveys (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    version INTEGER NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    languages TEXT DEFAULT '["en"]', -- JSON array of language codes
    is_active BOOLEAN DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS questions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    survey_id INTEGER,
    question_index INTEGER,
    short_name TEXT,
    question_text_json TEXT NOT NULL, -- JSON object: {"en": "text", "es": "texto", ...}
    audio_files_json TEXT, -- JSON object: {"en": "file1.mp3", "es": "file2.mp3", ...}
    question_type TEXT DEFAULT 'multiple_choice',
    validation_script TEXT,
    validation_error_json TEXT DEFAULT '{"en": "Invalid answer"}', -- JSON object for error messages
    pre_script TEXT,
    FOREIGN KEY (survey_id) REFERENCES surveys(id)
);

CREATE TABLE IF NOT EXISTS options (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    question_id INTEGER,
    option_index INTEGER,
    option_text_json TEXT NOT NULL, -- JSON object: {"en": "text", "es": "texto", ...}
    audio_files_json TEXT, -- JSON object: {"en": "file1.mp3", "es": "file2.mp3", ...}
    option_value TEXT,
    FOREIGN KEY (question_id) REFERENCES questions(id)
);

-- Upload tracking
CREATE TABLE IF NOT EXISTS uploads (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    survey_response_id TEXT UNIQUE NOT NULL,
    facility_id INTEGER,
    upload_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    file_path TEXT,
    status TEXT,
    participant_id TEXT,
    FOREIGN KEY (facility_id) REFERENCES facilities(id)
);

-- Recruitment management
CREATE TABLE IF NOT EXISTS recruitment_pools (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    criteria TEXT,
    target_size INTEGER,
    sampling_rate REAL,
    is_active BOOLEAN DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS coupons (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    code TEXT UNIQUE NOT NULL,
    pool_id INTEGER,
    distributor_survey_id TEXT,
    recipient_survey_id TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    used_at DATETIME,
    FOREIGN KEY (pool_id) REFERENCES recruitment_pools(id)
);

-- Audit logging
CREATE TABLE IF NOT EXISTS audit_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT,
    action TEXT NOT NULL,
    entity_type TEXT,
    entity_id TEXT,
    old_value TEXT,
    new_value TEXT,
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Admin users
CREATE TABLE IF NOT EXISTS admin_users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    is_active BOOLEAN DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    last_login DATETIME
);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_uploads_facility ON uploads(facility_id);
CREATE INDEX IF NOT EXISTS idx_uploads_status ON uploads(status);
CREATE INDEX IF NOT EXISTS idx_questions_survey ON questions(survey_id);
CREATE INDEX IF NOT EXISTS idx_options_question ON options(question_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_entity ON audit_log(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_coupons_code ON coupons(code);
`;

// Function to create sample survey
function createSampleSurvey(db, callback) {
    console.log('Creating sample survey...');
    
    // Insert sample survey
    db.run(
        `INSERT INTO surveys (version, name, description, languages, is_active) 
         VALUES (?, ?, ?, ?, ?)`,
        [1, 'SALT HIV Survey', 'Sample HIV monitoring survey for key populations', '["en", "sw"]', 1],
        function(err) {
            if (err) {
                console.error('Error creating sample survey:', err);
                callback();
                return;
            }
            
            const surveyId = this.lastID;
            console.log('Sample survey created with ID:', surveyId);
            
            // Create sample questions
            const questions = [
                {
                    question_index: 0,
                    short_name: 'consent',
                    question_text: '{"en": "Do you consent to participate in this survey?", "sw": "Je, unakubali kushiriki katika utafiti huu?"}',
                    question_type: 'multiple_choice',
                    options: [
                        { option_index: 0, text: '{"en": "Yes", "sw": "Ndio"}', value: '1' },
                        { option_index: 1, text: '{"en": "No", "sw": "Hapana"}', value: '0' }
                    ]
                },
                {
                    question_index: 1,
                    short_name: 'age',
                    question_text: '{"en": "What is your age?", "sw": "Una umri gani?"}',
                    question_type: 'numeric',
                    validation_script: 'value >= 18 && value <= 100',
                    validation_error: '{"en": "Age must be between 18 and 100", "sw": "Umri lazima uwe kati ya 18 na 100"}',
                    pre_script: 'consent == "1"'
                },
                {
                    question_index: 2,
                    short_name: 'gender',
                    question_text: '{"en": "What is your gender?", "sw": "Jinsia yako ni gani?"}',
                    question_type: 'multiple_choice',
                    pre_script: 'consent == "1"',
                    options: [
                        { option_index: 0, text: '{"en": "Male", "sw": "Mwanaume"}', value: 'male' },
                        { option_index: 1, text: '{"en": "Female", "sw": "Mwanamke"}', value: 'female' },
                        { option_index: 2, text: '{"en": "Other", "sw": "Nyingine"}', value: 'other' }
                    ]
                },
                {
                    question_index: 3,
                    short_name: 'hiv_tested',
                    question_text: '{"en": "Have you been tested for HIV in the last 12 months?", "sw": "Je, umepimwa VVU katika miezi 12 iliyopita?"}',
                    question_type: 'multiple_choice',
                    pre_script: 'consent == "1" && age >= 18',
                    options: [
                        { option_index: 0, text: '{"en": "Yes", "sw": "Ndio"}', value: 'yes' },
                        { option_index: 1, text: '{"en": "No", "sw": "Hapana"}', value: 'no' },
                        { option_index: 2, text: '{"en": "Prefer not to answer", "sw": "Napendelea kutokjibu"}', value: 'no_answer' }
                    ]
                },
                {
                    question_index: 4,
                    short_name: 'test_result',
                    question_text: '{"en": "What was your most recent HIV test result?", "sw": "Matokeo yako ya hivi karibuni ya kipimo cha VVU yalikuwa yapi?"}',
                    question_type: 'multiple_choice',
                    pre_script: 'hiv_tested == "yes"',
                    options: [
                        { option_index: 0, text: '{"en": "Negative", "sw": "Hasi"}', value: 'negative' },
                        { option_index: 1, text: '{"en": "Positive", "sw": "Chanya"}', value: 'positive' },
                        { option_index: 2, text: '{"en": "Prefer not to answer", "sw": "Napendelea kutokjibu"}', value: 'no_answer' }
                    ]
                },
                {
                    question_index: 5,
                    short_name: 'on_treatment',
                    question_text: '{"en": "Are you currently on HIV treatment?", "sw": "Je, kwa sasa unapata matibabu ya VVU?"}',
                    question_type: 'multiple_choice',
                    pre_script: 'test_result == "positive"',
                    options: [
                        { option_index: 0, text: '{"en": "Yes", "sw": "Ndio"}', value: 'yes' },
                        { option_index: 1, text: '{"en": "No", "sw": "Hapana"}', value: 'no' }
                    ]
                }
            ];
            
            let questionsCreated = 0;
            
            questions.forEach(q => {
                db.run(
                    `INSERT INTO questions (survey_id, question_index, short_name, question_text_json, 
                     question_type, validation_script, validation_error_json, pre_script) 
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
                    [surveyId, q.question_index, q.short_name, q.question_text, 
                     q.question_type, q.validation_script || null, q.validation_error || null, q.pre_script || null],
                    function(err) {
                        if (err) {
                            console.error('Error creating question:', err);
                            return;
                        }
                        
                        const questionId = this.lastID;
                        
                        // Add options if it's a multiple choice question
                        if (q.options) {
                            q.options.forEach(opt => {
                                db.run(
                                    `INSERT INTO options (question_id, option_index, option_text_json, option_value) 
                                     VALUES (?, ?, ?, ?)`,
                                    [questionId, opt.option_index, opt.text, opt.value]
                                );
                            });
                        }
                        
                        questionsCreated++;
                        if (questionsCreated === questions.length) {
                            console.log('Sample survey with', questions.length, 'questions created successfully');
                            callback();
                        }
                    }
                );
            });
        }
    );
}

// Execute SQL statements
db.exec(createTables, (err) => {
    if (err) {
        console.error('Error creating tables:', err);
        process.exit(1);
    }
    console.log('Database tables created successfully');
    
    // Create default admin user (password: admin123)
    const bcrypt = require('bcrypt');
    const defaultPassword = 'admin123';
    const saltRounds = 10;
    
    bcrypt.hash(defaultPassword, saltRounds, (err, hash) => {
        if (err) {
            console.error('Error hashing password:', err);
            db.close();
            return;
        }
        
        db.run(
            `INSERT OR IGNORE INTO admin_users (username, password_hash) VALUES (?, ?)`,
            ['admin', hash],
            (err) => {
                if (err) {
                    console.error('Error creating default admin user:', err);
                } else {
                    console.log('Default admin user created (username: admin, password: admin123)');
                }
                
                // Create sample survey
                createSampleSurvey(db, () => {
                    // Close database
                    db.close((err) => {
                        if (err) {
                            console.error('Error closing database:', err);
                        } else {
                            console.log('Database initialization complete');
                        }
                    });
                });
            }
        );
    });
});