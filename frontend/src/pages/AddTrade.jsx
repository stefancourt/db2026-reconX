// TICKET-ADV123 — React Hook Form + Yup validation.
import React from 'react';
import { useForm } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import { withAuth } from '@components/withAuth.jsx';
import { api } from '@services/apiService.js';

// TODO(TICKET-ADV123): build a yup.object schema covering every field on the
//   form. Suggested validators:
//     tradeRef       — string, regex /^[A-Z]{3}-\d{8}-\d{4}$/ ("AAA-YYYYMMDD-NNNN")
//     instrumentId   — integer, positive
//     counterpartyId — integer, positive
//     assetClass     — oneOf ['EQUITY','FX','BOND','DERIVATIVE']
//     side           — oneOf ['BUY','SELL']
//     quantity       — positive number
//     price          — positive number
//     tradeDate      — date
const schema = yup.object({});

function AddTrade() {
  const { register, handleSubmit, formState: { errors, isSubmitting }, reset } =
        useForm({ resolver: yupResolver(schema) });

  async function onSubmit(values) {
    // TODO(TICKET-ADV123): call api.createTrade(values), reset the form on
    //                     success, surface server errors back to the user.
  }

  return (
    <section>
      <h2>Add trade</h2>
      <form onSubmit={handleSubmit(onSubmit)} className="trade-form">
        {/* TODO(TICKET-ADV123): wire up <input {...register('tradeRef')} /> for
            every field listed in the schema above. Render
            errors.<field>.message under each input when present. */}
        <label>Trade ref   <input {...register('tradeRef')} placeholder="EQU-20260603-0001" /></label>
        {errors.tradeRef && <p className="form-error">{errors.tradeRef.message}</p>}

        <button disabled={isSubmitting} type="submit">Submit</button>
      </form>
    </section>
  );
}

export default withAuth(AddTrade);
