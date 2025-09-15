const express = require('express');
const path = require('path');
const cors = require('cors');
const helmet = require('helmet');
const morgan = require('morgan');
const compression = require('compression');
const session = require('express-session');
require('dotenv').config();

const app = express();
const PORT = process.env.PORT || 3000;

// Security middleware
app.use(helmet({
    contentSecurityPolicy: {
        directives: {
            defaultSrc: ["'self'"],
            styleSrc: ["'self'", "'unsafe-inline'"],
            scriptSrc: ["'self'", "'unsafe-inline'", "'unsafe-eval'"],
            scriptSrcAttr: ["'unsafe-inline'"],
            mediaSrc: ["'self'", "blob:", "data:"],
            connectSrc: ["'self'", "blob:", "data:"],
            workerSrc: ["'self'", "blob:"],
        },
    },
}));

// CORS configuration
app.use(cors({
    origin: process.env.CORS_ORIGIN || '*',
    credentials: true
}));

// Compression
app.use(compression());

// Logging
app.use(morgan('combined'));

// Body parsing
app.use(express.json({ limit: '50mb' }));
app.use(express.urlencoded({ extended: true, limit: '50mb' }));

// Static files
app.use(express.static(path.join(__dirname, '..', 'public')));
app.use('/js/lamejs', express.static(path.join(__dirname, '..', 'node_modules', 'lamejs')));

// Session configuration for web UI
app.use(session({
    secret: process.env.SESSION_SECRET || 'salt-management-secret-key-change-in-production',
    resave: false,
    saveUninitialized: false,
    cookie: {
        secure: process.env.NODE_ENV === 'production',
        httpOnly: true,
        maxAge: 1000 * 60 * 60 * 24 // 24 hours
    }
}));

// View engine setup
app.set('view engine', 'ejs');
app.set('views', path.join(__dirname, 'web', 'views'));

// Import routes
const authRoutes = require('./api/routes/auth');
const surveyRoutes = require('./api/routes/surveys');
const facilityRoutes = require('./api/routes/facilities');
const surveyConfigRoutes = require('./api/routes/surveyConfigSimple');
const surveySyncRoutes = require('./api/routes/surveySync');
const surveyUploadRoutes = require('./api/routes/surveyUpload');
const facilityConfigRoutes = require('./api/routes/facilityConfig');
const webDashboardRoutes = require('./web/routes/dashboard');
const webSurveyEditorRoutes = require('./web/routes/surveyEditor');

// API Routes
app.use('/api/auth', authRoutes);
app.use('/api/surveys', surveyRoutes);
app.use('/api/admin/facilities', facilityRoutes);
app.use('/api/admin/survey-config', surveyConfigRoutes);
app.use('/api/sync', surveySyncRoutes);
app.use('/api/sync', surveyUploadRoutes);
app.use('/api/sync', facilityConfigRoutes);

// Web Routes
app.use('/', webDashboardRoutes);
app.use('/', webSurveyEditorRoutes);

// Health check endpoint
app.get('/health', (req, res) => {
    res.json({ 
        status: 'OK', 
        timestamp: new Date().toISOString(),
        uptime: process.uptime()
    });
});

// 404 handler
app.use((req, res, next) => {
    if (req.path.startsWith('/api')) {
        res.status(404).json({ error: 'Endpoint not found' });
    } else {
        res.status(404).render('pages/404', { title: 'Page Not Found' });
    }
});

// Error handler
app.use((err, req, res, next) => {
    console.error('Error:', err);
    
    if (req.path.startsWith('/api')) {
        res.status(err.status || 500).json({
            error: process.env.NODE_ENV === 'production' 
                ? 'Internal server error' 
                : err.message
        });
    } else {
        res.status(err.status || 500).render('pages/error', {
            title: 'Error',
            message: process.env.NODE_ENV === 'production' 
                ? 'Something went wrong' 
                : err.message
        });
    }
});

// Start server
app.listen(PORT, () => {
    console.log(`SALT Management Server running on port ${PORT}`);
    console.log(`Environment: ${process.env.NODE_ENV || 'development'}`);
    console.log(`API endpoint: http://localhost:${PORT}/api`);
    console.log(`Web UI: http://localhost:${PORT}`);
});