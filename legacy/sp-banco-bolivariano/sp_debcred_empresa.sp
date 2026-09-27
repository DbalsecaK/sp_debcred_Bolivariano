create procedure dbo.sp_debcred_empresa
(       

@s_ssn                  int         = null,

@s_user                 varchar(14) = null,

@s_term                 varchar(30) = null,

@s_srv                  varchar(30) = null,

@s_ofi                  smallint    = null,

@i_aplcobis             char(1)     = 'N',

@i_sp_name              varchar(32),

@i_orden                int,

@i_orden_empresa        int,

@i_fecha_proceso        datetime,

@i_nem_emp              char(15),

@i_empresa              int,

@i_producto             smallint,

@i_servicio             varchar(10),

@i_frm_pagcob           char(3),

@i_frm_pagcob_spi       char(3) = null,

@i_canal                char(3) = 'DIR',

@i_tipo_afec            char(2) = '10',

@i_referencia           varchar(100),

@i_mon_debito           smallint,

@i_pais_cta             smallint,

@i_cod_banco_cta        smallint,

@i_tipcta_emp           smallint,

@i_numcta_emp           char(10),

@i_valor_ordenado       money,

@i_valor_debito         money,

@i_comision             money, -- UN SOLO MOVIMIENTO DEBITO + COMISION

@i_valor_comision       money, -- COMISION Y DEBITO SEPARADOS

@i_tipo_referencia      char(1),

@i_tarjeta              varchar(24) = null, --REF6:GCC 

@i_ref_prov             varchar(24),

@i_opcion               char(2),

@i_tipo_pagcob          char(1),

@i_localidad_orden      smallint,

@i_tipo_proceso         char(1),

@i_frm_pagcob_deb       char(3)=null,

@i_valor2_swift         money = 0 , --ref8:wcj

@i_secuencial           int = 0, --REF10:RMC

@i_cod_swift            varchar(20) = null, --Ref13:PGH

@o_error                int output,

@o_reg_a_proc           int output,

@i_valor_tarifa	        money = null,  -- REF33 thernanp 

@i_valor_comision_cue	money = null,  -- REF33 thernanp 

@i_valor_tarifa_efe	    money = null,  -- REF33 thernanp 

@i_valor_comision_efe	money = null,  -- REF33 thernanp 

@i_valor_tarifa_che	    money = null,  -- REF33 thernanp 

@i_valor_comision_che	money = null  -- REF33 thernanp 

) 

AS 



Declare @w_return     int,

        @w_nombre_cuenta  varchar(64),

        @w_moneda   char(2),--ref16:gcc

        @w_trn      int,

        @w_causal   char(4), --Ref37

        @w_cadena   varchar(30),

        @w_cod_errord   int, 

        @w_sts_proc   char(1),

        @w_num_error    int,    --REF2:GGR

        @w_empresa_str          varchar(10),

        @w_savepoint    varchar(32),   --REF2:GGR

        @w_valor_comision money, 

        @w_concepto     varchar(10), --REF17:JCB

        @w_ente     int,   --ref18:gcc

        @w_serv_origen          varchar(10),  --ref22:gcc

        @w_act_totord   char(1), --ref22:gcc

        --@w_desc_concepto  varchar(30),      --ref22:gcc

        

        @wRowdbBiz    int, ---ref Optimizacion SAT  

        --ref27: ini thp

        @w_tran_ncnd    int,    --ref27:thp

        @w_cantidad   int,    --ref27:thp

        @w_comision_und money,  --ref27:thp

        @w_val_por_tran money,

        @w_batch        bit,    --ref31:MGR

        @w_concepto_bas varchar (5) --ref31:MGR 

       ,@w_dt_referencia_grupo varchar(20)  -- ref35 VP

       ,@w_nombre_cuenta_d   varchar(64) --ref35 VP

       ,@w_msg  varchar(64) --ref35 VP

       ,@w_serv_pago_dir varchar(4) --ref35 VP

       ,@w_bloqNoti char(1)



set @w_cantidad = 0   --ref27:thp

set @w_comision_und = 0 --ref27:thp

 

--ref31:MGR ini

if @i_aplcobis = 'S' select @w_batch = 0

if @i_aplcobis = 'N' select @w_batch = 1

--ref31:MGR fin



 --cambio para generar los movimiento por debito y para comision por separado--18/11/2013

  --ref28: tvv inicio

  if @i_comision  > 0 and @i_valor_comision=0 

  begin

     select @i_valor_comision = @i_comision

     select @i_comision=0

  end

 --ref28: tvv fin



-- OBTIENE VALOR COMISION POR TRANSACCION (SE LE COBRA A LA EMPRESA)

execute @w_return = db_biz_admempresa..sp_con_comision

        @i_cod_empresa          = @i_empresa,

        @i_cod_producto         = @i_producto,

        @i_cod_servicio         = @i_servicio,

        @i_cod_canal            = @i_canal,   --REF22:GGR

        @i_cod_alcance          = 'E',

        @i_tip_comision         = '01',

        @i_secuencia            = 1,

        @i_aplcobis             = @i_aplcobis,

        @o_valor_por_tran       = @w_val_por_tran output

if @w_return <> 0

    select @w_val_por_tran = 0



if isnull(@i_comision, 0) > 0    

  set @w_comision_und = @i_comision



if isnull(@i_valor_comision, 0) > 0    

  set @w_comision_und = @i_valor_comision



if @w_val_por_tran > 0  

  set @w_cantidad = @w_comision_und / @w_val_por_tran

  

set @w_comision_und = @w_val_por_tran

--ref27: fin thp



select @w_moneda = convert(char(2), @i_mon_debito), --ref16:gcc

       @o_error = 0,   --REF2:GGR

       @w_act_totord = 'S' --ref22:gcc



         select @i_frm_pagcob_deb = isnull(@i_frm_pagcob_deb,@i_frm_pagcob)



select @s_ssn = isnull(@s_ssn, 0),   --REF2:GGR

       @w_trn = 0,

       @w_causal = ' ',

       @w_moneda = convert(char(2), @i_mon_debito) --ref16:gcc



--<REF 41

if @i_servicio = 'SPI'

begin

    select @w_concepto_bas = @s_term,

	       @s_term = ' ' -- se reversa el valor inicial

end

else

begin

    --ref31:MGR ini

    select @w_concepto_bas = '0'

    if @i_tipcta_emp = 12

    begin

      if exists (select 1 from cob_virtuales..vi_cuenta

                 where vi_cta_banco = @i_numcta_emp

                 and vi_prod_banc = 13) --Verificar cuenta basica

         select @w_concepto_bas = '91'

    end

    --ref31:MGR fin

end

--REF 41>



--ref32:Mesv Para obtener la TRX y Causa por la forma de pago SPI

if @i_servicio ='TRANSQUICK' and @i_frm_pagcob_spi is not null

	execute @w_return = db_biz_admempresa..sp_con_confcontable

           @i_producto   = @i_producto,

           @i_servicio   = @i_servicio,

           @i_tipoafec   = @i_tipo_afec,

           @i_frm_pagcob = @i_frm_pagcob_spi, --se envio COB por la configuracion contable

           @i_canal      = @i_canal,  

           @i_tipcta     = @i_tipcta_emp,

           @i_moneda     = @w_moneda,

           @i_referencia = @i_tipo_referencia, 

           @i_empresa     = @i_empresa, 

           @o_trn        = @w_trn output,

           @o_cau        = @w_causal output



else

execute @w_return = db_biz_admempresa..sp_con_confcontable

           @i_producto   = @i_producto,

           @i_servicio   = @i_servicio,

           @i_tipoafec   = @i_tipo_afec,

           @i_frm_pagcob = @i_frm_pagcob,

           @i_canal      = @i_canal,    -- wcj 2003/08/28

           @i_tipcta     = @i_tipcta_emp,

           @i_moneda     = @w_moneda,

           @i_referencia = @i_tipo_referencia, -- wcj 2003/08/28

           @i_empresa    = @i_empresa, 

           @i_concepto   = @w_concepto_bas, --ref31:MGR

           @o_trn        = @w_trn output,

           @o_cau        = @w_causal output



--print '@w_trn: %1!, @w_causal:%2!', @w_trn, @w_causal



if @w_return > 0 or @@error <> 0

--REF2:GGR Inicio1

begin

   --No existe configuracion contable.

   select @w_num_error = 120000

   goto lbl_error

end

--REF2:GGR Fin1



--REF2:GGR Inicio2

begin tran



   select @w_savepoint = 'sp_debito_empresa'

   save tran @w_savepoint

--REF2:GGR Fin2



   if @i_tipcta_emp in (3, 4, 12) --ref31:MGR

   begin

    --ref23:GCC ini

    

    select  @w_cadena   = convert(varchar(10), @i_orden_empresa)

    --ref23:GCC fin



    --REF6:GCC 

    if ltrim(rtrim(@i_servicio)) = 'TRANSWIFT' 

    begin

         -- select  @w_cadena = 'ORD DE PAGO: ' + convert(varchar(10), @i_orden_empresa) --ref11:gcc

       -- Ref13:PGH

          select @w_cadena = 'COD:' + @i_cod_swift

    end

    else

    begin

      

          select  @w_cadena   = convert(varchar(10), @i_orden_empresa)

    end



    --ref15:wcj inicio

		if ltrim(rtrim(@i_servicio)) = 'IMPADUAN'  

		begin

			  select @i_cod_swift = isnull(@i_cod_swift,'')



			  if ltrim(rtrim(@i_cod_swift)) = 'CORPEI'  

					   --select @w_causal = '512' --REF17:JCB 



				 -------------------------------------------------------------

				 --REF17:JCB SE AGREGA LLAMADA A SP PARA QUE RETORNE CAUSAL

				 -------------------------------------------------------------

			 begin



				select @w_concepto = '99999', @w_causal = null



				select @w_concepto = b.ct_cod_catalogo

				  from db_biz_admempresa..ba_tabla, 

					   db_biz_admempresa..ba_catalogo b

				 where tb_cod_tabla = ct_cod_tabla

				   and tb_nom_tabla ='ad_concepto_contable'

				   and isnull(ct_otro_campo_catalogo, '') = 'NDCORPEI'

				   and ct_est_catalogo = 'A'



				execute @w_return = db_biz_admempresa..sp_con_confcontable

						   @i_producto   = @i_producto,

						   @i_servicio   = @i_servicio,

						   @i_tipoafec   = @i_tipo_afec,

						   @i_frm_pagcob = @i_frm_pagcob,

						   @i_canal      = @i_canal,    

						   @i_tipcta     = @i_tipcta_emp,

						   @i_moneda     = @w_moneda,

						   @i_referencia = @i_tipo_referencia, 

						   @i_empresa    = @i_empresa, 

						   @i_concepto   = @w_concepto, 

						   @o_trn        = @w_trn output,

						   @o_cau        = @w_causal output



				select @w_causal = isnull(@w_causal, '512')

				

				if @w_return > 0 

				--REF2:GGR Inicio1

				begin

				   --No existe configuracion contable.

				   select @w_num_error = 120000

				   goto lbl_error

				end

			 end

         ------------------------------------------

         end

    --ref15:wcj fin



    --ref22:gcc ini

    if ltrim(rtrim(@i_servicio)) = 'SPI' and @i_tipo_afec = '12' --Devoluciones SPI4

    begin

		/* ref39: ini

		--select @w_ref_prov = '', @w_tarjeta = ''

		select @w_desc_concepto= ''

		select @w_desc_concepto= ct_cod_catalogo + '-'   + left(ct_nom_catalogo, 27) + '  '

		from db_biz_admempresa..ba_tabla,

			 db_biz_admempresa..ba_catalogo

		where tb_nom_tabla = 'ad_concepto_spi'

		 and ct_cod_tabla = tb_cod_tabla

		 and ct_cod_catalogo = '01' --Transferencias Clientes



		if @i_tipcta_emp = 3

		begin

		 select @w_cadena = @w_desc_concepto

		end

		else

		if @i_tipcta_emp = 4

		begin

		 select @w_cadena  = @w_desc_concepto

		end

		else

		if @i_tipcta_emp = 12      --ref31:MGR

		begin

		 select @w_cadena  = @w_desc_concepto

		end

		ref39: fin */

		select @w_cadena  = convert(varchar(10), @i_orden_empresa)

    end

    --ref22:gcc fin



--select @i_valor_debito,@i_comision,@w_comision_und,@i_mon_debito



        if @i_tipcta_emp = 12 --ref31:MGR ini

            begin

                exec @w_return  = cob_virtuales..sp_vi_ndc_automatica 

                     @s_srv             = @s_srv,

                     @s_ofi             = 0,

                     @s_user            = @s_user,

                     @s_term            = @s_term ,

                     @t_trn             = @w_trn,

                     @i_cta             = @i_numcta_emp,

                     @i_val             = @i_valor_debito,

                     @i_cau             = @w_causal,

                     @i_mon             = @i_mon_debito,

                     @i_empresa         = @i_empresa,

                     @i_canal           = 'SAT',

                     @i_verf_estado_cta = 'S',

                     @i_batch           = @w_batch,

                     @i_ref             = @w_cadena,

		     @i_alt		=@i_orden, --ref36:Mesv 06/04/2018 se envía la orden

                     @o_error           = @w_cod_errord out,

                     @o_ssn_monet       = @w_tran_ncnd out  



                select @w_cod_errord = @w_return



            end

        else

            begin  --ref31:MGR fin

                execute @w_return = sp_ndc_ahcc

                        @s_ssn          = @s_ssn,

                        @s_srv          = @s_srv,

                        @s_user         = @s_user,

                        @s_term         = @s_term,

                        @s_ofi          = 0,

                        @s_date         = @i_fecha_proceso,

                        @t_trn          = @w_trn,

                        @i_cuenta       = @i_numcta_emp,

                        @i_tipo_cuenta  = @i_tipcta_emp,

                        @i_causal       = @w_causal,

                        @i_valor        = @i_valor_debito,

                        @i_mon          = @i_mon_debito,

                        @i_tarjeta  = @i_tarjeta, --REF6:GCC 

                        @i_servicio = @i_servicio, --REF6:GCC

                        @i_ref          = @w_cadena,

                        @i_aplcobis     = @i_aplcobis,

                        @i_detalle      = @i_ref_prov,

                        @i_tcomision    = @i_comision, -- wcj 2003/08/28

                        @i_canal        = @i_canal,

                        @i_frm_pagcob   = @i_frm_pagcob_spi,

                        @i_alt    = @i_orden, --ref12:gcc

                        @i_alterno_dos  = @i_frm_pagcob,  --ref23:gcc --ref27:thp forma pago

                        @i_orden_banco  = @i_orden,   --ref23:thp OrdenBanco

                        @i_secuencial   = 0,    --ref23:thp Secuencial

                        @i_nchq    = @w_cantidad ,    --ref27:thp cant registros

                        @i_solca     = @w_comision_und ,  --ref27:thp comision unitaria

                        @o_transaccion  = @w_tran_ncnd out    --ref27:thp sec transaccion

            

      



                select @w_cod_errord = @w_return

            end



    --ref9:gcc ini

    if @w_return = 0  and ltrim(rtrim(@i_servicio)) <> 'PAGOPRV' --REF11:GCC

    begin



        declare @w_serv_sms varchar(10), @w_proddeb varchar(3), @w_prodcre varchar(3), @w_tip_cta smallint,

                @w_desc_canal varchar(16), @w_valor_sms varchar(11), @w_emp_inst varchar(32), 

          @w_costo_sms varchar(11),  @w_canal_sms char(3), @w_cta_cre varchar(30)



              select @w_canal_sms = @i_canal



        if @w_canal_sms in ('DIR', 'SAT')

        begin

       select @w_canal_sms = 'SAT'

             select @w_desc_canal = 'SAT'

        end

        else

        if @w_canal_sms = 'BNK'

        begin

          select @w_canal_sms = 'IBK'

          select @w_desc_canal = '24OnLine'

        end

        else

        if @w_canal_sms = 'VEN'

        begin

          select @w_desc_canal = 'Ventanilla'

        end



            select @w_serv_sms = ltrim(rtrim(substring(ct_cod_catalogo,1,patindex('%-%',ct_cod_catalogo) - 1)))

            from db_biz_admempresa..ba_tabla,db_biz_admempresa..ba_catalogo

            where  tb_cod_tabla = ct_cod_tabla

            and  tb_nom_tabla ='ad_servicios_sms'

            and  ct_nom_catalogo like '%' + ltrim(rtrim(@i_servicio)) + '%'

            and  right(ltrim(rtrim(ct_cod_catalogo)), 3) = @w_canal_sms

            and  ct_est_catalogo = 'A'



        if @w_serv_sms is not null

        begin

          

          if ltrim(rtrim(@i_servicio)) = 'TRANSWIFT'

          begin

			set @wRowdbBiz=0

			  select @w_emp_inst = dt_referencia_grupo,

			   @w_cta_cre  = substring(dt_nom_cuenta,1, 30)

			  from db_biz_pagos..bp_orden, db_biz_pagos..bp_detalle

			  where or_orden_banco = @i_orden

			  and or_orden_banco = dt_orden_banco

			  and dt_secuencial = @i_secuencial --REF10:RMC

			  and or_ordenante  = @i_empresa  --REF10:RMC

			set @wRowdbBiz=@@rowcount ---ref Optimizacion SAT

			if @wRowdbBiz<=0  ---ref Optimizacion SAT

			begin

				select @w_emp_inst = dt_referencia_grupo,

				@w_cta_cre  = substring(dt_nom_cuenta,1, 30)

				from db_sat_his..bp_orden_his, db_sat_his..bp_detalle_his

				where or_orden_banco = @i_orden

				and or_orden_banco = dt_orden_banco

				and dt_secuencial = @i_secuencial --REF10:RMC

				and or_ordenante  = @i_empresa  --REF10:RMC         

			end

			  select @w_valor_comision = @i_valor_comision

          end 

          else

          begin

			  select @w_emp_inst = null

			  select @w_cta_cre  = null

          end 



          if ltrim(rtrim(@i_servicio)) in ('TRANSCLI', 'TARJCRED', 'COMEXT')

          begin

			  select  @i_comision, @i_valor_ordenado

			  select @w_valor_comision =  @i_comision 

			  select @i_valor_debito   =  @i_valor_ordenado

		

			set @wRowdbBiz=0

			  select @w_emp_inst = substring(C.ct_nom_catalogo, 10, 32),

				   @w_tip_cta  = dt_tipo_cta,

				   @w_cta_cre  = dt_numero_cuenta

			  from db_biz_pagos..bp_detalle, db_biz_admempresa..ba_tabla, db_biz_admempresa..ba_catalogo C

			  where dt_orden_banco = @i_orden

			  and dt_referencia_grupo = substring(ct_nom_catalogo, 1, 9)

			  and tb_nom_tabla = 'ad_cuentas_bce' 

				and tb_est_tabla = 'A' 

			  and tb_cod_tabla = ct_cod_tabla 

			  and ct_est_catalogo = 'A'

			set @wRowdbBiz=@@rowcount ---ref Optimizacion SAT

			if @wRowdbBiz<=0  ---ref Optimizacion SAT

			begin

				select @w_emp_inst = substring(C.ct_nom_catalogo, 10, 32),

				@w_tip_cta  = dt_tipo_cta,

				@w_cta_cre  = dt_numero_cuenta

				from db_sat_his..bp_detalle_his, db_biz_admempresa..ba_tabla, db_biz_admempresa..ba_catalogo C

				where dt_orden_banco = @i_orden

				and dt_referencia_grupo = substring(ct_nom_catalogo, 1, 9)

				and tb_nom_tabla = 'ad_cuentas_bce' 

				and tb_est_tabla = 'A' 

				and tb_cod_tabla = ct_cod_tabla 

				and ct_est_catalogo = 'A'       

			end



			

			  if @w_tip_cta  = 0

				 select @w_prodcre = null

			  else

			  if @w_tip_cta = 3

					   select @w_prodcre = 'CTE'

					else 

					if @w_tip_cta = 4

					   select @w_prodcre = 'AHO'					   					   					   

	 

          end 



          /*Ref 30 LBP 10/27/2015 */

          if @w_tip_cta =9 

             select @w_prodcre = 'CON'

          if @w_tip_cta =8 

             select @w_prodcre = 'ESP'

          /*Ref 30 LBP 10/27/2015 */  	

					   	

          select @w_valor_comision = isnull(@w_valor_comision, 0) + isnull(@i_valor2_swift, 0)



          select  @w_valor_sms = convert(varchar(11),@i_valor_debito)   

          select  @w_costo_sms = convert(varchar(11),@w_valor_comision)   

		

		  

	  --Inicio ref35 VP 	

         select @w_serv_pago_dir = null	  

		 		  

		select @w_serv_pago_dir = ct_otro_campo_catalogo 

		  from db_biz_admempresa..ba_tabla, db_biz_admempresa..ba_catalogo c

		 where tb_nom_tabla  = 'ad_notificacion_basica'  and

			   tb_cod_tabla  = ct_cod_tabla    and

			   ct_cod_catalogo = @i_servicio and

			   tb_est_tabla  = 'A'  and

			   ct_est_catalogo = 'A' 



		select @w_serv_pago_dir = isnull(@w_serv_pago_dir, 'OTRO')



		 

  	     If @w_serv_pago_dir = 'B'

		   begin		   		    		       			   		

			

			  Select @w_nombre_cuenta_d = dt_nombre_beneficiario, @w_dt_referencia_grupo = dt_referencia_grupo 

				from bp_detalle

				where dt_orden_banco   = @i_orden

				/********optimización his ****/

				if @@rowcount=0

				begin

				 Select @w_nombre_cuenta_d = dt_nombre_beneficiario,  @w_dt_referencia_grupo = dt_referencia_grupo 

				 from db_sat_his..bp_detalle_his

				 where dt_orden_banco   = @i_orden

				 /******/

				 				 

				end

						

			  exec @w_return =  pa_sat_pnotificacion 			

						@i_canal			= @i_canal,				

						@i_ctadebito        = @i_numcta_emp,

						@i_tipctadeb        = @i_tipcta_emp,

						@i_servicio			= @i_servicio,

						@i_orden_banco		= @i_orden,

						@i_direccion_transf = @w_dt_referencia_grupo,

						@i_secuencial 	    = @i_secuencial ,

						@i_valor            = @i_valor_ordenado,				

						@i_nombrecred	    = @w_nombre_cuenta_d,

						@i_comision         = @i_valor_comision,

						@i_ctacred          = @w_cta_cre,

						@i_prod_cre         = @w_prodcre,

						@i_empresa          = @w_emp_inst,

						@o_error		= @o_error output,

						@o_msg			= @w_msg output								

				 

			

		   end 

		   --Fin ref35 VP 	

		   else

		      If @w_serv_pago_dir = 'OTRO'

			   begin 	

			   

			    --ref18:gcc ini

				  if @i_tipcta_emp = 3

				  begin

					select @w_proddeb = 'CTE'



					  select @w_ente   = cc_cliente 			        

					  from  cob_cuentas..cc_ctacte

					  where cc_cta_banco = @i_numcta_emp

				  end

				  else 

				  if @i_tipcta_emp = 4

				  begin

					select @w_proddeb = 'AHO'



					select @w_ente = ah_cliente 			      

					 from cob_ahorros..ah_cuenta

					where ah_cta_banco = @i_numcta_emp

				  end

				  else

				  if @i_tipcta_emp = 12 --ref31:MGR

				  begin

					select @w_proddeb = 'VIR'



					select @w_ente = vi_cliente,

						   @w_proddeb = case when vi_prod_banc = 13 then 'AHO' else 'VIR' end --ref31:MGR			      

					  from cob_virtuales..vi_cuenta

					 where vi_cta_banco = @i_numcta_emp

				  end				  

				--ref18:gcc fin

				

				-- <REF40 JHC

				select @w_bloqNoti = 'N'

				

				select @w_bloqNoti = 'S'

				  from db_biz_admempresa..ba_tabla, 

					   db_biz_admempresa..ba_catalogo b

				 where tb_cod_tabla = ct_cod_tabla

				   and tb_nom_tabla ='ba_bloqueaNotificacionSAT'

				   and isnull(ct_nom_catalogo, '') = rtrim(@i_servicio) + '-' + rtrim(@w_serv_sms) /*<REF 43 HMC, REF 43>*/

				   and isnull(ct_otro_campo_catalogo,'') = rtrim(@i_sp_name) /*<REF 43 HMC, REF 43>*/

				   and ct_est_catalogo = 'A'

				-- REF40 JHC>

				--<REF 41

					declare @v_notiBCE char(1)

					set @v_notiBCE = 'N'

					if 	@w_canal_sms = 'BCE' and @i_servicio = 'ROLPAGO' and @i_numcta_emp is null

						begin 

							set @v_notiBCE = 'S'

						end	

				--REF 41>

				

				  if @w_bloqNoti <> 'S' -- REF40 JHC 

				  begin

					if 	@v_notiBCE = 'N' /*<REF 41 HMC, REF 41>*/

                      begin 	/*<REF 41 HMC, REF 41>*/									

        				  exec @w_return = cob_internet..sp_eventos

        					   @i_operacion    = 'I',

        					   @i_canal    =  @w_canal_sms, 

        					   @i_servicio     =  @w_serv_sms,

        					   @i_producto             =  @i_tipcta_emp, 

        					   @i_cuenta               =  @i_numcta_emp, 

        					   @i_valor                =  @w_valor_sms, 

        					   @i_cta_deb              =  @i_numcta_emp, 

        					   @i_prod_deb             =  @w_proddeb, 

        					   @i_cta_cre              =  @w_cta_cre, 

        					   @i_prod_cre             =  @w_prodcre, 

        					   @i_cliente    =  @w_ente, --ref18:gcc

        					   @i_costo                =  @w_costo_sms, 

        					   @i_empresa              =  @w_emp_inst, 

        					   @i_desc_canal           =  @w_desc_canal 			      

                       end	/*<REF 41 HMC, REF 41>*/

					  

				  end 

				end  

      select @w_cod_errord = @w_return



        end --if serv

    end --if return

    --ref9:gcc fin

   end

   else

   begin

      if @i_tipcta_emp in (9)            

      begin

		   /*<REF 45 JPI INI, REF 45>*/

		  declare @v_saldo_ap money , @v_valor_debito money

		  select @v_valor_debito = @i_valor_debito

		  select @w_empresa_str= convert(varchar(10),@i_empresa)

		  if @w_empresa_str = '1295' and @i_servicio ='SPI'

		  select @v_saldo_ap = @v_valor_debito ,  @i_valor_debito = 0

	        /*<REF 45 JPI FIN, REF 45>*/

		   

                     execute @w_return= db_biz_pagos..sp_graba_tran_servicio

                     @s_srv = @s_srv,   @s_ofi = @s_ofi,  @s_ssn = null,     @s_user= @s_user,

                     @s_term= @s_term,  @t_trn = @w_trn,     @i_fecha =@i_fecha_proceso,

                     @i_referencia =@i_ref_prov,           @i_cta_banco =@i_numcta_emp,

                     @i_oficina  = @s_ofi, @i_indicador =1,@i_moneda =@i_mon_debito      ,

                     @i_causa = @w_causal, @i_saldo =  @v_saldo_ap,     @i_valor =  @i_valor_debito,

                     @i_oficina_cta= 0 ,@i_tipo_chequera =@w_empresa_str

                     ,@i_orden_banco  = @i_orden, @i_secuencial = 0 --ref23:thp orden + Secuencial

                     select @w_cod_errord = @w_return





      end

      else                

      --REF2:GGR Inicio3

      begin

         --El tipo de cuenta debe ser Corriente (3), Ahorros(4) o Contable (9).

         select @w_cod_errord = 122001

      end

      --REF2:GGR Fin3

   end



   --REF2:GGR Inicio4

   --si no hubo error al hacer la nota de debito -> grabar mov.

   --si hubo error al hacer la nota de debito -> reversar, grabar mov. con error y salir

   select @w_sts_proc = 'P'

   if @w_cod_errord != 0

   begin

      rollback tran @w_savepoint

      select @w_sts_proc = 'X'

   end



   --ref22:gcc ini

   if ltrim(rtrim(@i_servicio)) = 'SPI' and @i_tipo_afec = '12' --Devoluciones SPI4

   begin

  select @w_serv_origen = or_servicio

  from   db_biz_pagos..bp_orden

  where  or_orden_banco = @i_orden  

  set @wRowdbBiz=@@rowcount ---ref Optimizacion SAT

  if @wRowdbBiz<=0  ---ref Optimizacion SAT

  begin

  select @w_serv_origen = or_servicio

  from   db_sat_his..bp_orden_his

  where  or_orden_banco = @i_orden

  end



  select @i_servicio = @w_serv_origen



  select @w_act_totord = 'N'

   end

   --ref22:gcc fin



   -- COMISION DE ORDENES POR SERVICIO SPI SE CREA UN SOLO MOVIMIENTO

   --GRABA MOVIMIENTO Y FORMA DE PAGO DEL DEBITO

  

  -- select @i_valor_debito,@i_mon_debito,@i_comision

   execute @w_return = sp_grb_mov_y_frmpgo

           @s_user                     = @s_user,

           @s_term                     = @s_term,

           @s_ofi                      = @s_ofi,

           @t_trn                      = @w_trn,

           @i_tipo_proceso             = @i_tipo_proceso,

           @i_empresa                  = @i_empresa,

           @i_producto                 = @i_producto,

           @i_orden_banco              = @i_orden,

           @i_canal                    = @i_canal, -- wcj 2003/08/28

           @i_cau                      = @w_causal,

           @i_est_proceso              = @w_sts_proc,

           @i_cod_error                = @w_cod_errord,

           @i_frm_pagcob               = @i_frm_pagcob,

           @i_moneda_orden             = @i_mon_debito,

           @i_valor_mov                = @i_valor_debito,

           @i_tipo_afectacion          = '1',

           @i_fch_contab               = @i_fecha_proceso,

           @i_referencia               = @i_referencia,

           @i_secuencial               = 0,

           @i_servicio                 = @i_servicio,

           @i_tipo_pagcob              = @i_tipo_pagcob,

           @i_pais_cta                 = @i_pais_cta,

           @i_cod_banco_cta            = @i_cod_banco_cta,

           @i_tipo_cta                 = @i_tipcta_emp,

           @i_numero_cta               = @i_numcta_emp,

           @i_valor_ordenado           = @i_valor_ordenado,

           @i_nem_ordenante            = @i_nem_emp,

           @i_localidad_pagcob         = @i_localidad_orden,

           @i_nombre_cuenta            = @w_nombre_cuenta,

           @i_nombre_beneficiario      = @w_nombre_cuenta,

           @i_orden_empresa            = @i_orden_empresa,

           @i_valor_comision           = @i_comision,           -- wcj 2003/08/28

           @i_tran_ncnd         = @w_tran_ncnd      --ref27:thp sec transaccion



   if @w_return != 0 or @@error != 0

   begin

 

      --Error al grabar movimiento y forma de pago.

      select @w_num_error = 122002

      goto lbl_error

   end



   if @w_cod_errord != 0

   begin

  

      commit tran

      select @o_error = @w_cod_errord

      return @o_error

   end





--ref32:Mesv Envio la forma de pago COB para obtener la trx y causa de la comision 

if @i_servicio ='TRANSQUICK' and @i_frm_pagcob_spi is not null

select @i_frm_pagcob = @i_frm_pagcob_spi

   --si la comision se debe grabar por separado del valor del servicio

if @i_servicio='TRANSBIMO' --REF036:Mesv 03/04/2018

select @i_valor_tarifa=@i_valor_comision

  

   if @i_valor_comision > 0

   begin

  

      exec @w_return = sp_grb_comision

           @s_ssn                 = @s_ssn,

           @s_srv                 = @s_srv,

           @s_user                = @s_user,

           @s_term                = @s_term,

           @s_ofi                 = @s_ofi,

           @i_aplcobis            = @i_aplcobis,

           @i_sp_name             = @i_sp_name,

           @i_fecha_proceso       = @i_fecha_proceso,

           @i_canal_comision      = @i_canal,

           @i_empresa             = @i_empresa,

           @i_producto            = @i_producto,

           @i_servicio            = @i_servicio,

           @i_tipo_proceso        = @i_tipo_proceso,

           @i_orden_banco         = @i_orden,

           @i_valor_comision      = @i_valor_comision,

           @i_cadena              = @w_cadena,

           @i_tarjeta             = @i_tarjeta, --REF6:GCC

           @i_frm_pagcob          = @i_frm_pagcob,

           @i_moneda              = @i_mon_debito,

           @i_tipcta_emp          = @i_tipcta_emp,

           @i_numcta_emp          = @i_numcta_emp,

           @i_referencia          = 'COBRO DE COMISION',--@i_referencia,

           @i_detalle_ref         = @i_ref_prov, --REF6:GCC

           @i_tipo_pagcob         = @i_tipo_pagcob,

           @i_pais_cta            = @i_pais_cta,

           @i_cod_banco_cta       = @i_cod_banco_cta,

           @i_nem_ordenante       = @i_nem_emp,

           @i_localidad_pagcob    = @i_localidad_orden,

           @i_nombre_cuenta       = @w_nombre_cuenta,

           @i_nombre_beneficiario = @w_nombre_cuenta,

           @i_orden_empresa       = @i_orden_empresa,

           @i_tipo_horario        = @i_tipo_referencia,

           @i_savepoint           = @w_savepoint,

           @i_secuencial          = 0,  --ref23:thp   

           @o_error               = @w_cod_errord output

           ,@i_valor_tarifa		= @i_valor_tarifa 	--REF33 thernanp

           , @i_valor_comision_cue		=	@i_valor_comision_cue	-- REF33 thernanp 

	   , @i_valor_tarifa_efe		=	@i_valor_tarifa_efe 	-- REF33 thernanp 

	   , @i_valor_comision_efe		=	@i_valor_comision_efe	-- REF33 thernanp 

	   , @i_valor_tarifa_che		=	@i_valor_tarifa_che	  -- REF33 thernanp 

	   , @i_valor_comision_che		=	@i_valor_comision_che	 -- REF33 thernanp 





      if @w_return != 0 or @@error != 0 or @w_cod_errord != 0		--REF34 THERNANP

      begin

         --Error al generar debito por comision.

         select @w_num_error = 122003

         if @w_cod_errord != 0 select @w_num_error = @w_cod_errord	--REF34 THERNANP

         --goto lbl_error

         /*<REF 44 JPI INI, REF 44>*/

         select @w_sts_proc = 'X'

         if @@trancount > 0

            rollback tran

		 

		 execute @w_return = sp_grb_mov_y_frmpgo

           @s_user                     = @s_user,

           @s_term                     = @s_term,

           @s_ofi                      = @s_ofi,

           @t_trn                      = @w_trn,   

           @i_tipo_proceso             = @i_tipo_proceso,

           @i_empresa                  = @i_empresa,

           @i_producto                 = @i_producto,

           @i_orden_banco              = @i_orden,

           @i_canal                    = @i_canal, 

           @i_cau                      = @w_causal,  

           @i_est_proceso              = @w_sts_proc,

           @i_cod_error                = @w_cod_errord,

           @i_frm_pagcob               = @i_frm_pagcob,

           @i_moneda_orden             = @i_mon_debito,

           @i_valor_mov                = @i_valor_comision,

           @i_tipo_afectacion          = '1',

           @i_fch_contab               = @i_fecha_proceso,

           @i_referencia               = 'COBRO DE COMISION',

           @i_secuencial               = 0,

           @i_servicio                 = @i_servicio,

           @i_tipo_pagcob              = @i_tipo_pagcob,

           @i_pais_cta                 = @i_pais_cta,

           @i_cod_banco_cta            = @i_cod_banco_cta,

           @i_tipo_cta                 = @i_tipcta_emp,

           @i_numero_cta               = @i_numcta_emp,

           @i_valor_ordenado           = @i_valor_ordenado, 

           @i_nem_ordenante            = @i_nem_emp,

           @i_localidad_pagcob         = @i_localidad_orden,

           @i_nombre_cuenta            = @w_nombre_cuenta,

           @i_nombre_beneficiario      = @w_nombre_cuenta,

           @i_orden_empresa            = @i_orden_empresa,

           @i_valor_comision           = @i_comision,   

           @i_tran_ncnd         = @w_tran_ncnd  

		 

		 select @o_error = @w_cod_errord

         return @o_error

		 

		 /*<REF 44 JPI FIN, REF 44>*/

		 

      end



      --si hay error al debitar la comision -> se reversa, se graba

      --el movimiento de comision con error (en grb_comision) y se retorna

      if @w_cod_errord != 0

      begin

   

         commit tran

         select @o_error = @w_cod_errord

         select @o_error

         return @o_error

      end

   end

   --REF2:GGR Fin4





   --ref8:wcj inicio

   if isnull(@i_valor2_swift, 0)  > 0 and ltrim(rtrim(@i_servicio)) = 'TRANSWIFT'

      begin

        exec @w_return = sp_grb_comision

           @s_ssn                 = @s_ssn,

           @s_srv                 = @s_srv,

           @s_user                = @s_user,

           @s_term                = @s_term,

           @s_ofi                 = @s_ofi,

           @i_aplcobis            = @i_aplcobis,

           @i_sp_name             = @i_sp_name,

           @i_fecha_proceso       = @i_fecha_proceso,

           @i_canal_comision      = @i_canal,

           @i_empresa             = @i_empresa,

           @i_producto            = @i_producto,

           @i_servicio            = @i_servicio,

           @i_tipo_proceso        = @i_tipo_proceso,

           @i_orden_banco         = @i_orden,

           @i_valor_comision      = @i_valor2_swift,

           @i_cadena              = @w_cadena,

           @i_tarjeta     = @i_tarjeta, --REF6:GCC

           @i_frm_pagcob          = @i_frm_pagcob,

           @i_moneda              = @i_mon_debito,

           @i_tipcta_emp          = @i_tipcta_emp,

           @i_numcta_emp          = @i_numcta_emp,

           @i_referencia          = @i_referencia,

           @i_detalle_ref   = @i_ref_prov, --REF6:GCC

           @i_tipo_pagcob         = @i_tipo_pagcob,

           @i_pais_cta            = @i_pais_cta,

           @i_cod_banco_cta       = @i_cod_banco_cta,

           @i_nem_ordenante       = @i_nem_emp,

           @i_localidad_pagcob    = @i_localidad_orden,

           @i_nombre_cuenta       = @w_nombre_cuenta,

           @i_nombre_beneficiario = @w_nombre_cuenta,

           @i_orden_empresa       = @i_orden_empresa,

           @i_tipo_horario        = @i_tipo_referencia,

              @i_tipoafec      = '16',

           @i_savepoint           = @w_savepoint,

           @i_secuencial          = 0,--ref23:thp   

           @o_error               = @w_cod_errord output

           ,@i_valor_tarifa		= @i_valor2_swift 	--REF33 thernanp



         if @w_return != 0 or @@error != 0 or @w_cod_errord != 0 	--REF34 THERNANP

         begin

           --Error al generar debito por comision.

           select @w_num_error = 122003

           if @w_cod_errord != 0 select @w_num_error = @w_cod_errord	--REF34 THERNANP

           goto lbl_error

         end



         --si hay error al debitar la comision -> se reversa, se graba

         --el movimiento de comision con error (en grb_comision) y se retorna

         if @w_cod_errord != 0

         begin

           commit tran

           select @o_error = @w_cod_errord

           return @o_error

         end

      end --ref8:wcj fin





   --REF2:GGR Inicio5

   --solo si no hubo error al debitar el valor x servicio

   --o el valor por comision (en caso que aplique) 

   --se actualizan los estados en total_orden

   --REF2:GGR Fin5



            -- ACTUALIZA CABECERA DE ORDEN A ESTADO DE TRANSICION

      if @i_canal = 'DIR' or @i_canal = 'SFR'   --REF3:GGR

      or @i_canal ='FR2' or @i_canal = 'BTH' --ref5 cmeg07Feb2006 --ref26:gcc

      or @i_canal = 'VEN' --ref29:MGR

      begin

              if @i_opcion = '01' begin

                update bp_total_orden

                set te_estado_proceso = 'T',

                    te_codigo_error   = @w_cod_errord

                where te_orden_banco  = @i_orden

                  and te_frm_pagcob  in (@i_frm_pagcob_deb)

                  and te_servicio     = @i_servicio

      and isnull(te_estado_proceso, 'I') = 'I'  -- DSA 20030818

              

          set @wRowdbBiz=@@rowcount ---ref Optimizacion SAT

          if @wRowdbBiz<=0  ---ref Optimizacion SAT

          begin

              update db_sat_his..bp_total_orden_his

              set te_estado_proceso = 'T',

              te_codigo_error   = @w_cod_errord

              where te_orden_banco  = @i_orden

              and te_frm_pagcob  in (@i_frm_pagcob_deb)

              and te_servicio     = @i_servicio

              and isnull(te_estado_proceso, 'I') = 'I'  -- DSA 20030818 

              set @wRowdbBiz=@@rowcount ---ref Optimizacion SAT

          end

          

          

      end

                if @i_opcion = '02' begin

                    update bp_total_orden

                    set te_estado_proceso = 'T',

                        te_codigo_error   = @w_cod_errord

                    where te_orden_banco = @i_orden

                      and te_frm_pagcob  in ('CUE', 'EFE','CHL')

                      and te_servicio    = @i_servicio

          and isnull(te_estado_proceso, 'I') = 'I'  -- DSA 20030818

        

                        

        set @wRowdbBiz=@@rowcount ---ref Optimizacion SAT

          if @wRowdbBiz<=0  ---ref Optimizacion SAT

          begin

              update db_sat_his..bp_total_orden_his

              set te_estado_proceso = 'T',

              te_codigo_error   = @w_cod_errord

              where te_orden_banco = @i_orden

              and te_frm_pagcob  in ('CUE', 'EFE','CHL')

              and te_servicio    = @i_servicio

              and isnull(te_estado_proceso, 'I') = 'I'  -- DSA 20030818

              set @wRowdbBiz=@@rowcount ---ref Optimizacion SAT

          end

        

        end

                

                    if @i_opcion = '03' begin

                        update bp_total_orden

                        set te_estado_proceso = 'T',

                            te_codigo_error   = @w_cod_errord

                        where te_orden_banco = @i_orden

                          and te_frm_pagcob  in ('CUE', 'EFE', 'CHE')

                          and te_servicio    = @i_servicio

        and isnull(te_estado_proceso, 'I') = 'I'  -- DSA 20030818

         set @wRowdbBiz=@@rowcount  ---ref Optimizacion SAT

          if @wRowdbBiz<=0  ---ref Optimizacion SAT

          begin

            update db_sat_his..bp_total_orden_his

                        set te_estado_proceso = 'T',

                            te_codigo_error   = @w_cod_errord

                        where te_orden_banco = @i_orden

                        and te_frm_pagcob  in ('CUE', 'EFE', 'CHE')

                        and te_servicio    = @i_servicio

            and isnull(te_estado_proceso, 'I') = 'I'  -- DSA 20030818

            set @wRowdbBiz=@@rowcount ---ref Optimizacion SAT

            

          end

        end

     end

     else

     begin

              update bp_total_orden

              set te_estado_proceso   = 'T',

                    te_codigo_error   = @w_cod_errord

              where te_orden_banco    = @i_orden

                  and te_frm_pagcob  in ('COB', 'TRC', 'CTB', 'CPD', 'TPD')--ref19:gcc

                  and te_servicio     = @i_servicio

      and isnull(te_estado_proceso, 'I') = 'I'

          set @wRowdbBiz=@@rowcount ---ref Optimizacion SAT

          if @wRowdbBiz<=0  ---ref Optimizacion SAT

          begin

            update db_sat_his..bp_total_orden_his

            set te_estado_proceso   = 'T',

            te_codigo_error   = @w_cod_errord

            where te_orden_banco    = @i_orden

            and te_frm_pagcob  in ('COB', 'TRC', 'CTB', 'CPD', 'TPD')--ref19:gcc

            and te_servicio     = @i_servicio

            and isnull(te_estado_proceso, 'I') = 'I'

            set @wRowdbBiz=@@rowcount ---ref Optimizacion SAT

          end

     end



--REF2:GGR Inicio6

   if @wRowdbBiz = 0

   begin  

  if @i_servicio not in('TRANSWIFT','IMPADUAN','PAGIESS', 'TRANSQUICK', 'TRANSBIMO', 'PAGOPRV') and @w_act_totord = 'S'--ref14:wcj--ref21:pgh --ref22:gcc --ref32:Mesv  08 Junio 2016 --ref36:Mesv --ref:38:jqp

  begin 

          select @w_num_error = 122004

          goto lbl_error

      end --ref7:wcj   

   end



   -- ESTE CAMBIO SE HIZO POR REPORTES DE TARJETAS DE PAGO

   --ref20:Pgh Se quita ya que cuentas virtuales no esta en el SAT 

/*   If @i_servicio = 'ROLPAGO'

   begin

      update bp_orden

         set or_fch_inicio_pagcob = @i_fecha_proceso

       where or_orden_banco = @i_orden

         and or_servicio = @i_servicio

      if @@error != 0

      begin

         --Error al actualizar fecha de inicio de pago de la orden.

         select @w_num_error = 122005

         goto lbl_error

      end

   end

*/

   --REF4:GGR se borra codigo



commit tran



select @o_error = isnull(@o_error, 0)

return 0



lbl_error:

   if @@trancount > 0

      rollback tran



   if @i_aplcobis = 'S'

   begin

      exec cobis..sp_cerror

           @t_from     = @i_sp_name,

           @i_num      = @w_num_error

      return @w_num_error

   end

   else

   begin

      select @o_error = @w_num_error

      return 0

   end

--REF2:GGR Fin6

