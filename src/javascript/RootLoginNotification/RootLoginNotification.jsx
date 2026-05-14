import React, {useEffect, useRef, useState} from 'react';
import {useMutation, useQuery} from '@apollo/client';
import {useTranslation} from 'react-i18next';
import {Button, Loader, Typography} from '@jahia/moonstone';
import {CKEditor} from '@ckeditor/ckeditor5-react';
import {
    Autoformat,
    Bold,
    ClassicEditor,
    Essentials,
    Italic,
    Link,
    List,
    ListProperties,
    Paragraph,
    RemoveFormat,
    SourceEditing,
    Strikethrough,
    TextTransformation,
    Underline
} from 'ckeditor5';
import styles from './RootLoginNotification.scss';
import {GET_SETTINGS, SAVE_SETTINGS} from './RootLoginNotification.gql';

const isValidEmail = val => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(val);

const editorConfig = {
    licenseKey: 'GPL',
    plugins: [
        Autoformat,
        Bold,
        Essentials,
        Italic,
        Link,
        List,
        ListProperties,
        Paragraph,
        RemoveFormat,
        SourceEditing,
        Strikethrough,
        TextTransformation,
        Underline
    ],
    toolbar: {
        items: [
            'undo',
            'redo',
            '|',
            'bold',
            'italic',
            'underline',
            'strikethrough',
            'removeFormat',
            '|',
            'link',
            '|',
            'bulletedList',
            'numberedList',
            '|',
            'sourceEditing'
        ]
    },
    menuBar: {isVisible: false},
    list: {
        properties: {
            styles: false,
            startIndex: false,
            reversed: false
        }
    },
    link: {
        defaultProtocol: 'https://'
    }
};

export const RootLoginNotificationAdmin = () => {
    const {t} = useTranslation('root-login-notification');
    const [saveStatus, setSaveStatus] = useState(null);
    const [errors, setErrors] = useState({recipient: '', sender: ''});
    const recipientInputRef = useRef(null);
    const senderInputRef = useRef(null);
    const saveLiveRef = useRef(null);

    useEffect(() => {
        document.title = `${t('label.title')} — Jahia Administration`;
    }, [t]);

    const [formState, setFormState] = useState({
        recipient: '',
        sender: '',
        subject: '',
        body: ''
    });

    const {loading} = useQuery(GET_SETTINGS, {
        fetchPolicy: 'network-only',
        onCompleted: data => {
            const s = data?.rootLoginNotificationSettings;
            if (s) {
                setFormState({
                    recipient: s.recipient ?? '',
                    sender: s.sender ?? '',
                    subject: s.subject ?? '',
                    body: s.body ?? ''
                });
            }
        }
    });

    const [saveSettings, {loading: saving}] = useMutation(SAVE_SETTINGS);

    const validateEmailField = value => (value && !isValidEmail(value)) ? t('label.invalidEmail') : '';

    const handleChange = field => e => {
        setSaveStatus(null);
        const value = e.target.value;
        setFormState(prev => ({...prev, [field]: value}));
        if (errors[field] !== undefined) {
            setErrors(prev => ({...prev, [field]: validateEmailField(value)}));
        }
    };

    const handleBlur = field => () => {
        setErrors(prev => ({...prev, [field]: validateEmailField(formState[field])}));
    };

    const handleBodyChange = (event, editor) => {
        setSaveStatus(null);
        setFormState(prev => ({...prev, body: editor.getData()}));
    };

    const handleSave = async () => {
        const recipientError = validateEmailField(formState.recipient);
        const senderError = validateEmailField(formState.sender);
        setErrors({recipient: recipientError, sender: senderError});
        if (recipientError) {
            recipientInputRef.current?.focus();
            return;
        }

        if (senderError) {
            senderInputRef.current?.focus();
            return;
        }

        setSaveStatus(null);
        try {
            const result = await saveSettings({
                variables: {
                    recipient: formState.recipient || null,
                    sender: formState.sender || null,
                    subject: formState.subject,
                    body: formState.body
                }
            });
            setSaveStatus(result.data?.rootLoginNotificationSaveSettings ? 'success' : 'error');
        } catch (err) {
            console.error('Failed to save settings:', err);
            setSaveStatus('error');
        }

        setTimeout(() => saveLiveRef.current?.focus(), 50);
    };

    const hasErrors = Boolean(errors.recipient || errors.sender);

    const saveLiveMsg = saveStatus === 'success' ? t('label.saveSuccess') :
        saveStatus === 'error' ? t('label.saveError') : '';

    if (loading) {
        return (
            <div className={styles.rln_loading} role="status">
                <span className={styles.rln_sr_only}>{t('label.loading')}</span>
                <Loader size="big"/>
            </div>
        );
    }

    return (
        <div className={styles.rln_container}>
            {/* Persistent live region — always in DOM so AT registers it before status changes */}
            <div
                ref={saveLiveRef}
                tabIndex={-1}
                role={saveStatus === 'error' ? 'alert' : 'status'}
                aria-live={saveStatus === 'error' ? 'assertive' : 'polite'}
                aria-atomic="true"
                className={styles.rln_sr_only}
            >
                {saveLiveMsg}
            </div>

            <div className={styles.rln_header}>
                <h2>{t('label.title')}</h2>
            </div>

            <div className={styles.rln_description}>
                <Typography>{t('label.description')}</Typography>
            </div>

            <div className={styles.rln_form}>
                <div className={styles.rln_fieldGroup}>
                    <label className={styles.rln_label} htmlFor="rln-recipient">
                        {t('label.recipient')}
                    </label>
                    <span id="rln-recipient-hint" className={styles.rln_sr_only}>
                        {t('label.recipientPlaceholder')}
                    </span>
                    <input
                        ref={recipientInputRef}
                        type="email"
                        id="rln-recipient"
                        className={`${styles.rln_input}${errors.recipient ? ` ${styles['rln_input--error']}` : ''}`}
                        value={formState.recipient}
                        placeholder={t('label.recipientPlaceholder')}
                        autoComplete="email"
                        aria-invalid={Boolean(errors.recipient)}
                        aria-describedby={['rln-recipient-hint', errors.recipient ? 'rln-recipient-error' : ''].filter(Boolean).join(' ')}
                        onChange={handleChange('recipient')}
                        onBlur={handleBlur('recipient')}
                    />
                    {errors.recipient && (
                        <span id="rln-recipient-error" className={styles.rln_errorMsg}>{errors.recipient}</span>
                    )}
                </div>

                <div className={styles.rln_fieldGroup}>
                    <label className={styles.rln_label} htmlFor="rln-sender">
                        {t('label.sender')}
                    </label>
                    <span id="rln-sender-hint" className={styles.rln_sr_only}>
                        {t('label.senderPlaceholder')}
                    </span>
                    <input
                        ref={senderInputRef}
                        type="email"
                        id="rln-sender"
                        className={`${styles.rln_input}${errors.sender ? ` ${styles['rln_input--error']}` : ''}`}
                        value={formState.sender}
                        placeholder={t('label.senderPlaceholder')}
                        autoComplete="email"
                        aria-invalid={Boolean(errors.sender)}
                        aria-describedby={['rln-sender-hint', errors.sender ? 'rln-sender-error' : ''].filter(Boolean).join(' ')}
                        onChange={handleChange('sender')}
                        onBlur={handleBlur('sender')}
                    />
                    {errors.sender && (
                        <span id="rln-sender-error" className={styles.rln_errorMsg}>{errors.sender}</span>
                    )}
                </div>

                <div className={styles.rln_fieldGroup}>
                    <label className={styles.rln_label} htmlFor="rln-subject">
                        {t('label.subject')}
                    </label>
                    <input
                        type="text"
                        id="rln-subject"
                        className={styles.rln_input}
                        value={formState.subject}
                        aria-describedby="rln-subject-hint"
                        onChange={handleChange('subject')}
                    />
                    <span id="rln-subject-hint" className={styles.rln_fieldHint}>{t('label.subjectHint')}</span>
                </div>

                <div className={styles.rln_fieldGroup}>
                    {/* aria-hidden — CKEditor renders a contenteditable, not a native input;
                        the editor wrapper carries aria-labelledby instead */}
                    <span id="rln-body-label" className={styles.rln_label} aria-hidden="false">
                        {t('label.body')}
                    </span>
                    <div
                        className={`${styles.rln_editor}${saving ? ` ${styles['rln_editor--disabled']}` : ''}`}
                        aria-labelledby="rln-body-label"
                        aria-describedby="rln-body-hint"
                    >
                        <CKEditor
                            editor={ClassicEditor}
                            config={editorConfig}
                            disabled={saving}
                            data={formState.body}
                            onChange={handleBodyChange}
                        />
                    </div>
                    <span id="rln-body-hint" className={styles.rln_fieldHint}>{t('label.bodyHint')}</span>
                </div>
            </div>

            <div className={styles.rln_actions}>
                {saveStatus === 'success' && (
                    <div aria-hidden="true" className={`${styles.rln_alert} ${styles['rln_alert--success']}`}>
                        <span className={styles.rln_alertIcon}>✓</span> {t('label.saveSuccess')}
                    </div>
                )}
                {saveStatus === 'error' && (
                    <div aria-hidden="true" className={`${styles.rln_alert} ${styles['rln_alert--error']}`}>
                        <span className={styles.rln_alertIcon}>✕</span> {t('label.saveError')}
                    </div>
                )}
                <Button
                    label={t('label.save')}
                    variant="primary"
                    isDisabled={saving || hasErrors}
                    onClick={handleSave}
                />
            </div>
        </div>
    );
};

export default RootLoginNotificationAdmin;
