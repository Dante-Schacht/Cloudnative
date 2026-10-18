import { useState } from 'react'

const INITIAL = { customerName: '', product: '', quantity: '', notes: '' }

function validate(values) {
  const errors = {}
  if (!values.customerName.trim()) errors.customerName = 'El nombre del cliente es obligatorio.'
  if (!values.product.trim()) errors.product = 'El producto es obligatorio.'
  const quantity = Number(values.quantity)
  if (values.quantity.trim() === '') {
    errors.quantity = 'La cantidad es obligatoria.'
  } else if (!/^\d+$/.test(values.quantity.trim()) || quantity <= 0) {
    errors.quantity = 'La cantidad debe ser un número entero mayor a cero.'
  }
  return errors
}

export default function OrderForm({ onSubmit, disabled }) {
  const [values, setValues] = useState(INITIAL)
  const [errors, setErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)
  const [feedback, setFeedback] = useState(null)

  const handleChange = (event) => {
    const { name, value } = event.target
    setValues((prev) => ({ ...prev, [name]: value }))
  }

  const handleSubmit = async (event) => {
    event.preventDefault()
    if (disabled || submitting) return
    setFeedback(null)
    const found = validate(values)
    setErrors(found)
    if (Object.keys(found).length > 0) return

    setSubmitting(true)
    try {
      await onSubmit({
        customerName: values.customerName.trim(),
        product: values.product.trim(),
        quantity: Number(values.quantity),
        notes: values.notes.trim(),
      })
      setValues(INITIAL)
      setFeedback({ type: 'success', message: 'Pedido enviado correctamente.' })
    } catch (error) {
      setFeedback({ type: 'error', message: `No se pudo registrar el pedido. ${error.message}` })
    } finally {
      setSubmitting(false)
    }
  }

  const field = (name, label, props = {}) => (
    <div className="field">
      <label htmlFor={name}>{label}</label>
      <input
        id={name}
        name={name}
        value={values[name]}
        onChange={handleChange}
        aria-invalid={errors[name] ? 'true' : undefined}
        aria-describedby={errors[name] ? `${name}-error` : undefined}
        {...props}
      />
      {errors[name] && (
        <p id={`${name}-error`} className="field__error">{errors[name]}</p>
      )}
    </div>
  )

  return (
    <form className="card" onSubmit={handleSubmit} noValidate aria-labelledby="form-titulo">
      <h2 id="form-titulo">Registrar pedido</h2>
      {disabled && (
        <p className="notice notice--warning">
          El backend no está disponible. Podrás enviar el pedido cuando haya conexión.
        </p>
      )}
      {field('customerName', 'Nombre del cliente *', { type: 'text', autoComplete: 'off' })}
      {field('product', 'Producto *', { type: 'text', autoComplete: 'off' })}
      {field('quantity', 'Cantidad *', { type: 'number', min: 1, step: 1, inputMode: 'numeric' })}
      <div className="field">
        <label htmlFor="notes">Observaciones (opcional)</label>
        <textarea id="notes" name="notes" rows="3" value={values.notes} onChange={handleChange} />
      </div>
      <button type="submit" className="btn" disabled={disabled || submitting}>
        {submitting ? 'Enviando…' : 'Registrar pedido'}
      </button>
      {feedback && (
        <p role="status" className={`notice notice--${feedback.type}`}>{feedback.message}</p>
      )}
    </form>
  )
}
